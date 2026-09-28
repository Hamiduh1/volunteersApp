import Foundation
import FirebaseAuth
import FirebaseFirestore

@MainActor
final class EmailVerificationViewModel: ObservableObject {
    @Published var email = ""
    @Published var code = ""

    @Published var isLoading = false
    @Published var isResending = false
    @Published var resendCooldownSeconds = 0
    @Published var errorMessage: String?
    @Published var infoMessage: String?
    @Published var verificationSuccess = false

    private let db = Firestore.firestore()
    private let cooldownSeconds = 120
    private var cooldownTask: Task<Void, Never>?

    deinit {
        cooldownTask?.cancel()
    }

    func refreshSessionState(prefilledEmail: String?) async {
        if email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            email = prefilledEmail?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        }

        guard let currentUser = AuthService.shared.currentUser else {
            infoMessage = "Enter your account email and the latest 6-digit code."
            return
        }

        email = currentUser.email?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? email
        do {
            let snapshot = try await db.collection("users").document(currentUser.uid).getDocument()
            let lastSentAtMs = (snapshot.get("verificationEmailLastSentAtMs") as? NSNumber)?.doubleValue ?? 0
            let elapsedMs = Date().timeIntervalSince1970 * 1000 - lastSentAtMs
            let remaining = Int((Double(cooldownSeconds) * 1000 - elapsedMs) / 1000)
            if remaining > 0 {
                startCooldown(remaining)
            }
        } catch {
            // Keep UI responsive even if cooldown metadata lookup fails.
        }
    }

    func verifyCode() async {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let extractedCode = extractSixDigitCode(from: code)

        guard !cleanEmail.isEmpty else {
            errorMessage = "Enter your account email first."
            return
        }
        guard let extractedCode, !extractedCode.isEmpty else {
            errorMessage = "Enter the 6-digit verification code from your email."
            return
        }

        isLoading = true
        errorMessage = nil
        infoMessage = nil
        defer { isLoading = false }

        do {
            try await AuthService.shared.verifyEmailCode(email: cleanEmail, code: extractedCode)
            try? AuthService.shared.signOut()
            verificationSuccess = true
            infoMessage = "Email verified successfully. Please log in."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func resendVerificationEmail() async {
        guard resendCooldownSeconds == 0 else {
            infoMessage = "Please wait \(resendCooldownSeconds)s before resending."
            return
        }
        guard let user = AuthService.shared.currentUser else {
            errorMessage = "Session expired. Go to Login and use 'Resend verification email'."
            return
        }

        isResending = true
        errorMessage = nil
        infoMessage = nil
        defer { isResending = false }

        do {
            try await user.reload()
            if user.isEmailVerified {
                infoMessage = "Email already verified. Continue to login."
                return
            }

            let cooldown = try await AuthService.shared.requestEmailVerificationCode()
            startCooldown(cooldown)
            infoMessage = "Verification email sent. Enter the latest 6-digit code."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func startCooldown(_ seconds: Int) {
        let safeSeconds = max(0, seconds)
        cooldownTask?.cancel()
        resendCooldownSeconds = safeSeconds
        guard safeSeconds > 0 else { return }

        cooldownTask = Task { [weak self] in
            guard let self else { return }
            var remaining = safeSeconds
            while remaining > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                remaining -= 1
                await MainActor.run {
                    self.resendCooldownSeconds = remaining
                }
            }
        }
    }

    private func extractSixDigitCode(from input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if trimmed.range(of: #"^\d{6}$"#, options: .regularExpression) != nil {
            return trimmed
        }
        return trimmed.range(of: #"\b\d{6}\b"#, options: .regularExpression).map {
            String(trimmed[$0])
        }
    }
}
