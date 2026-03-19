import SwiftUI

struct ForgotPasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = ForgotPasswordViewModel()

    var body: some View {
        Form {
            Section {
                VStack(spacing: 8) {
                    HStack(spacing: 10) {
                        BrandSymbolView(
                            assetName: BrandAsset.appLogo,
                            fallbackSystemName: "person.3.sequence.fill",
                            size: 56,
                            useTemplate: false
                        )
                        BrandSymbolView(
                            assetName: BrandAsset.companyMark,
                            fallbackSystemName: "building.2.fill",
                            size: 28,
                            useTemplate: false
                        )
                    }
                    Text("Recover Your Account")
                        .font(.title3.weight(.semibold))
                    Text("Send a secure reset link for your app account")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
            }

            Section("Dashboard Home") {
                Text("Reset works for Volunteer, Organizer, Employer, and Staff accounts.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

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

            Section("Navigation") {
                Button("Back to Login") {
                    dismiss()
                }
                NavigationLink("Enter verification code") {
                    EmailVerificationView(prefilledEmail: viewModel.email)
                }
                NavigationLink("Create Account") {
                    SignUpView()
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
