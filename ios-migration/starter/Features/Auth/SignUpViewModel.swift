import Foundation

@MainActor
final class SignUpViewModel: ObservableObject {
    @Published var name = ""
    @Published var username = ""
    @Published var email = ""
    @Published var password = ""
    @Published var confirmPassword = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    func signUp() async {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanEmail.isEmpty else {
            errorMessage = "Email is required."
            return
        }
        guard cleanEmail.contains("@") else {
            errorMessage = "Enter a valid email address."
            return
        }
        guard password.count >= 6 else {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        guard password == confirmPassword else {
            errorMessage = "Passwords do not match."
            return
        }
        guard !cleanUsername.isEmpty else {
            errorMessage = "Username is required."
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            _ = try await AuthService.shared.signUp(
                email: cleanEmail,
                password: password,
                name: name,
                username: cleanUsername,
                role: .volunteer
            )
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
