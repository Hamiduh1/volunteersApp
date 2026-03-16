import Foundation
import Combine

struct ConversationRoute: Identifiable, Hashable {
    let id: String
}

@MainActor
final class ConversationsListViewModel: ObservableObject {
    @Published private(set) var conversations: [ChatConversationRecord] = []
    @Published private(set) var invitations: [UserInvitationRecord] = []
    @Published private(set) var updatingInvitationIds: Set<String> = []
    @Published var statusMessage: String?
    @Published var routeToConversation: ConversationRoute?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = ChatRepository()

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let conv = repository.fetchConversations(uid: user.uid)
            async let invites = repository.fetchInvitations(uid: user.uid)
            conversations = try await conv
            invitations = try await invites
        } catch {
            errorMessage = error.localizedDescription
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
            errorMessage = error.localizedDescription
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
            errorMessage = error.localizedDescription
        }
    }
}
