import Foundation

@MainActor
final class ForgotPasswordViewModel: ObservableObject {
    @Published var email = ""
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    func sendResetLink() async {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanEmail.isEmpty else {
            errorMessage = "Email is required."
            return
        }
        guard cleanEmail.contains("@") else {
            errorMessage = "Enter a valid email address."
            return
        }

        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            try await AuthService.shared.sendPasswordReset(email: cleanEmail)
            statusMessage = "Password reset link sent to \(cleanEmail)."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
