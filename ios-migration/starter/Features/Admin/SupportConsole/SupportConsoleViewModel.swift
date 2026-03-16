import Foundation
import Combine

@MainActor
final class SupportConsoleViewModel: ObservableObject {
    @Published private(set) var users: [SupportUserSummaryRecord] = []
    @Published var selectedUser: SupportUserSummaryRecord?
    @Published private(set) var details: SupportAccountDetailsRecord?

    @Published var query = ""
    @Published var verificationEmail = ""
    @Published var verificationPhone = ""
    @Published var associateEmail = ""

    @Published var isLoadingUsers = false
    @Published var isLoadingDetails = false
    @Published var isAddingAssociate = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    func loadUsers() async {
        isLoadingUsers = true
        errorMessage = nil
        defer { isLoadingUsers = false }

        do {
            users = try await repository.listSupportUsers(query: query)
            if let selected = selectedUser {
                selectedUser = users.first(where: { $0.id == selected.id })
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func selectUser(_ user: SupportUserSummaryRecord) {
        selectedUser = user
        details = nil
        verificationEmail = ""
        verificationPhone = ""
        statusMessage = nil
        errorMessage = nil
    }

    func loadDetails(canBypassVerification: Bool) async {
        guard let selectedUser else {
            errorMessage = "Select a user first."
            return
        }
        isLoadingDetails = true
        errorMessage = nil
        defer { isLoadingDetails = false }

        do {
            let detailsResult = try await repository.getSupportAccountDetails(
                userId: selectedUser.id,
                verificationEmail: canBypassVerification ? nil : verificationEmail.trimmingCharacters(in: .whitespacesAndNewlines),
                verificationPhone: canBypassVerification ? nil : verificationPhone.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            details = detailsResult
            statusMessage = canBypassVerification ? "Account details loaded." : "Verification passed. Account details loaded."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func addAssociate() async {
        let email = associateEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !email.isEmpty else {
            errorMessage = "Enter associate email."
            return
        }

        isAddingAssociate = true
        errorMessage = nil
        defer { isAddingAssociate = false }

        do {
            try await repository.addSupportAssociate(email: email)
            associateEmail = ""
            statusMessage = "Associate access granted for \(email)."
            await loadUsers()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
