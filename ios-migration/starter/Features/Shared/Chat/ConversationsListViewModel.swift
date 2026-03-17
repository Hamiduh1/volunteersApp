import Foundation
import Combine

struct ConversationRoute: Identifiable, Hashable {
    let id: String
}

enum InvitationStatusFilter: String, CaseIterable, Identifiable {
    case all
    case pending
    case accepted
    case declined

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
}

@MainActor
final class ConversationsListViewModel: ObservableObject {
    @Published private(set) var conversations: [ChatConversationRecord] = []
    @Published private(set) var invitations: [UserInvitationRecord] = []
    @Published private(set) var updatingInvitationIds: Set<String> = []
    @Published private(set) var initiatingCallKeys: Set<String> = []
    @Published var query = ""
    @Published var invitationFilter: InvitationStatusFilter = .pending
    @Published var statusMessage: String?
    @Published var routeToConversation: ConversationRoute?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = ChatRepository()

    var filteredConversations: [ChatConversationRecord] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return conversations }
        return conversations.filter { conversation in
            (conversation.lastMessage ?? "").lowercased().contains(cleanQuery)
                || (conversation.lastMessageText ?? "").lowercased().contains(cleanQuery)
                || (conversation.id ?? "").lowercased().contains(cleanQuery)
                || (conversation.otherParticipantName ?? "").lowercased().contains(cleanQuery)
                || (conversation.otherParticipantId ?? "").lowercased().contains(cleanQuery)
        }
    }

    var filteredInvitations: [UserInvitationRecord] {
        let byStatus: [UserInvitationRecord]
        switch invitationFilter {
        case .all:
            byStatus = invitations
        default:
            byStatus = invitations.filter { normalizedStatus($0.status) == invitationFilter.rawValue }
        }

        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return byStatus }
        return byStatus.filter { invite in
            (invite.senderName ?? invite.inviterName ?? "").lowercased().contains(cleanQuery)
                || (invite.senderEmail ?? "").lowercased().contains(cleanQuery)
                || (invite.senderId ?? "").lowercased().contains(cleanQuery)
                || (invite.context ?? "").lowercased().contains(cleanQuery)
        }
    }

    var pendingInvitationCount: Int {
        invitations.filter { normalizedStatus($0.status) == InvitationStatusFilter.pending.rawValue }.count
    }

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let conv = repository.fetchConversations(uid: user.uid)
            async let invites = repository.fetchInvitations(uid: user.uid)
            conversations = try await conv
            invitations = try await invites
            statusMessage = "Loaded \(conversations.count) conversations and \(pendingInvitationCount) pending invitations."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func acceptInvitation(_ invitation: UserInvitationRecord, user: AppSessionUser) async {
        let invitationId = invitation.id ?? (invitation.senderId ?? "")
        guard !invitationId.isEmpty else {
            errorMessage = "Invitation reference is missing."
            return
        }
        guard !updatingInvitationIds.contains(invitationId) else { return }
        updatingInvitationIds.insert(invitationId)
        defer { updatingInvitationIds.remove(invitationId) }

        do {
            let conversationId = try await repository.acceptInvitation(uid: user.uid, invitation: invitation)
            statusMessage = "Invitation accepted."
            if !conversationId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                routeToConversation = ConversationRoute(id: conversationId)
            }
            await refresh(user: user)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func declineInvitation(_ invitation: UserInvitationRecord, user: AppSessionUser) async {
        let invitationId = invitation.id ?? (invitation.senderId ?? "")
        guard !invitationId.isEmpty else {
            errorMessage = "Invitation reference is missing."
            return
        }
        guard !updatingInvitationIds.contains(invitationId) else { return }
        updatingInvitationIds.insert(invitationId)
        defer { updatingInvitationIds.remove(invitationId) }

        do {
            try await repository.declineInvitation(uid: user.uid, invitation: invitation)
            statusMessage = "Invitation declined."
            await refresh(user: user)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func startCall(
        conversation: ChatConversationRecord,
        user: AppSessionUser,
        isVideo: Bool
    ) async {
        let conversationId = conversation.id?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !conversationId.isEmpty else {
            errorMessage = "Conversation id is missing."
            return
        }

        let otherUid = resolveOtherParticipantId(conversation: conversation, currentUid: user.uid)
        guard let otherUid, !otherUid.isEmpty else {
            errorMessage = "Unable to resolve the other participant for this conversation."
            return
        }

        let key = "\(conversationId):\(isVideo ? "video" : "audio")"
        guard !initiatingCallKeys.contains(key) else { return }
        initiatingCallKeys.insert(key)
        defer { initiatingCallKeys.remove(key) }

        do {
            try await repository.initiateCall(
                conversationId: conversationId,
                callerUid: user.uid,
                receiverUid: otherUid,
                callType: isVideo ? "video" : "audio"
            )
            statusMessage = isVideo
                ? "Video call started. Waiting for the recipient."
                : "Voice call started. Waiting for the recipient."
            await refresh(user: user)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func startCallFromLog(
        conversationId: String?,
        peerUid: String?,
        user: AppSessionUser,
        isVideo: Bool
    ) async {
        let cleanConversationId = conversationId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let cleanPeerUid = peerUid?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !cleanConversationId.isEmpty, !cleanPeerUid.isEmpty else {
            errorMessage = "This call record cannot be redialed because chat metadata is missing."
            return
        }

        let key = "\(cleanConversationId):\(isVideo ? "video" : "audio")"
        guard !initiatingCallKeys.contains(key) else { return }
        initiatingCallKeys.insert(key)
        defer { initiatingCallKeys.remove(key) }

        do {
            try await repository.initiateCall(
                conversationId: cleanConversationId,
                callerUid: user.uid,
                receiverUid: cleanPeerUid,
                callType: isVideo ? "video" : "audio"
            )
            statusMessage = isVideo
                ? "Video redial started."
                : "Voice redial started."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func isInitiatingCall(conversationId: String?, isVideo: Bool) -> Bool {
        let cleanConversationId = conversationId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !cleanConversationId.isEmpty else { return false }
        let key = "\(cleanConversationId):\(isVideo ? "video" : "audio")"
        return initiatingCallKeys.contains(key)
    }

    private func resolveOtherParticipantId(
        conversation: ChatConversationRecord,
        currentUid: String
    ) -> String? {
        let explicit = conversation.otherParticipantId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !explicit.isEmpty, explicit != currentUid {
            return explicit
        }
        let participants = conversation.participants ?? []
        return participants.first { $0 != currentUid && !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private func normalizedStatus(_ rawStatus: String?) -> String {
        (rawStatus ?? "pending")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }
}

