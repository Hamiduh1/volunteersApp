import SwiftUI

struct ForgotPasswordView: View {
    @StateObject private var viewModel = ForgotPasswordViewModel()

    var body: some View {
        Form {
            Section("Reset Password") {
                TextField("Email", text: $viewModel.email)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .submitLabel(.send)
            }

            Section {
                Button(viewModel.isLoading ? "Sending..." : "Send Reset Link") {
                    Task { await viewModel.sendResetLink() }
                }
                .disabled(viewModel.isLoading)
            }

            if let statusMessage = viewModel.statusMessage {
                Section("Status") {
                    Text(statusMessage).foregroundStyle(.green)
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section("Error") {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Forgot Password")
    }
}
