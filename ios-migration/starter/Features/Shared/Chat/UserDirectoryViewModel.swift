import Foundation
import Combine

@MainActor
final class UserDirectoryViewModel: ObservableObject {
    @Published var searchQuery: String = ""
    @Published private(set) var allUsers: [DirectoryUserRecord] = []
    @Published private(set) var isLoading: Bool = false
    @Published private(set) var sendingInvitationUserIds: Set<String> = []
    @Published private(set) var sentInvitationUserIds: Set<String> = []
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = ChatRepository()

    var filteredUsers: [DirectoryUserRecord] {
        let cleanQuery = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return allUsers }
        return allUsers.filter { user in
            user.name.lowercased().contains(cleanQuery)
                || user.email.lowercased().contains(cleanQuery)
                || user.username.lowercased().contains(cleanQuery)
                || user.phoneNumber.lowercased().contains(cleanQuery)
        }
    }

    func refresh(currentUser: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            allUsers = try await repository.fetchDirectoryUsers(currentUid: currentUser.uid)
            statusMessage = "Loaded \(allUsers.count) users."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func sendInvitation(to recipient: DirectoryUserRecord, currentUser: AppSessionUser) async {
        guard !sentInvitationUserIds.contains(recipient.uid) else { return }
        guard !sendingInvitationUserIds.contains(recipient.uid) else { return }
        sendingInvitationUserIds.insert(recipient.uid)
        defer { sendingInvitationUserIds.remove(recipient.uid) }

        errorMessage = nil
        statusMessage = nil

        do {
            try await repository.sendDirectoryChatInvitation(sender: currentUser, recipient: recipient)
            sentInvitationUserIds.insert(recipient.uid)
            statusMessage = "Chat invitation sent!"
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func isInvitationSent(for userId: String) -> Bool {
        sentInvitationUserIds.contains(userId)
    }

    func isSendingInvitation(for userId: String) -> Bool {
        sendingInvitationUserIds.contains(userId)
    }
}
