import SwiftUI

struct EmailVerificationView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = EmailVerificationViewModel()
    private let prefilledEmail: String

    init(prefilledEmail: String = "") {
        self.prefilledEmail = prefilledEmail
    }

    var body: some View {
        Form {
            Section("Verify Email") {
                TextField("Email address", text: $viewModel.email)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
                TextField("Verification code", text: $viewModel.code)
                    .keyboardType(.numberPad)
            }

            Section {
                Button(viewModel.isLoading ? "Verifying..." : "Verify Code") {
                    Task { await viewModel.verifyCode() }
                }
                .disabled(viewModel.isLoading || viewModel.isResending)
            } footer: {
                Text("Open the latest verification email and enter the 6-digit code.")
            }

            Section {
                Button(
                    viewModel.resendCooldownSeconds > 0
                        ? "Resend email (\(viewModel.resendCooldownSeconds)s)"
                        : (viewModel.isResending ? "Sending..." : "Resend verification email")
                ) {
                    Task { await viewModel.resendVerificationEmail() }
                }
                .disabled(viewModel.isLoading || viewModel.isResending || viewModel.resendCooldownSeconds > 0)
            }

            if viewModel.verificationSuccess {
                Section("Status") {
                    Text("Email verified successfully. Please log in.")
                        .foregroundStyle(.green)
                    Button("Back to Login") {
                        dismiss()
                    }
                }
            }

            if let infoMessage = viewModel.infoMessage {
                Section("Info") {
                    Text(infoMessage).foregroundStyle(.secondary)
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section("Error") {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Verify Email")
        .task {
            await viewModel.refreshSessionState(prefilledEmail: prefilledEmail)
        }
    }
}
