import Foundation
import Combine

@MainActor
final class AccountSecurityViewModel: ObservableObject {
    @Published var isSendingReset = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    func sendPasswordReset(email: String) async {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanEmail.isEmpty else {
            errorMessage = "Email is required."
            return
        }

        isSendingReset = true
        errorMessage = nil
        defer { isSendingReset = false }

        do {
            try await AuthService.shared.sendPasswordReset(email: cleanEmail)
            statusMessage = "Password reset email sent to \(cleanEmail)."
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
