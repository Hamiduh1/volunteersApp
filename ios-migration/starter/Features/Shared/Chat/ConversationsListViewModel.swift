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
            routeToConversation = ConversationRoute(id: conversationId)
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

    private func normalizedStatus(_ rawStatus: String?) -> String {
        (rawStatus ?? "pending")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }
}

