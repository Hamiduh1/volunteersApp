import Foundation
import Combine

@MainActor
final class ConversationsListViewModel: ObservableObject {
    @Published private(set) var conversations: [ChatConversationRecord] = []
    @Published private(set) var invitations: [UserInvitationRecord] = []
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
}
