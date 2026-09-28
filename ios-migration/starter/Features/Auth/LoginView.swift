import SwiftUI

struct LoginView: View {
    @StateObject private var viewModel = LoginViewModel()

    private var roleSelectionBinding: Binding<String> {
        Binding(
            get: { viewModel.selectedRole },
            set: { viewModel.setSelectedRole($0) }
        )
    }

    var body: some View {
        Form {
            Section {
                VStack(spacing: 8) {
                    HStack(spacing: 10) {
                        BrandSymbolView(
                            assetName: BrandAsset.appLogo,
                            fallbackSystemName: "person.3.sequence.fill",
                            size: 64,
                            useTemplate: false
                        )
                        BrandSymbolView(
                            assetName: BrandAsset.companyMark,
                            fallbackSystemName: "building.2.fill",
                            size: 32,
                            useTemplate: false
                        )
                    }
                    Text("Welcome Back")
                        .font(.title3.weight(.semibold))
                    Text("Sign in to open your role dashboard")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
            }

            Section("Dashboard Home") {
                Text("Authenticate to continue to Volunteer, Organizer, Employer, or Admin dashboard.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Sign In") {
                TextField("Email", text: $viewModel.email)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .submitLabel(.next)
                SecureField("Password", text: $viewModel.password)
                    .textContentType(.password)
                    .submitLabel(.go)
            }

            Section(viewModel.showStaffRoles ? "Staff login" : "Log in as") {
                Picker("Account type", selection: roleSelectionBinding) {
                    ForEach(viewModel.activeRoleOptions) { option in
                        Text(option.label).tag(option.value)
                    }
                }

                Button(viewModel.showStaffRoles ? "Switch to user login" : "Staff login") {
                    viewModel.toggleRoleGroup()
                }
                .font(.subheadline)
            }

            Section {
                Button(viewModel.isLoading ? "Signing in..." : "Sign In") {
                    Task { await viewModel.signIn() }
                }
                .disabled(viewModel.isLoading || viewModel.isResendingVerification)
            }

            Section {
                Button(
                    viewModel.resendCooldownSeconds > 0
                        ? "Resend verification email (\(viewModel.resendCooldownSeconds)s)"
                        : (viewModel.isResendingVerification ? "Sending..." : "Resend verification email")
                ) {
                    Task { await viewModel.resendVerificationEmail() }
                }
                .disabled(
                    viewModel.isLoading ||
                        viewModel.isResendingVerification ||
                        viewModel.resendCooldownSeconds > 0
                )
            } footer: {
                Text("If login says not verified, request a new code here and enter the latest 6-digit code from email.")
            }

            Section("Account") {
                NavigationLink("Enter verification code") {
                    EmailVerificationView()
                }
                NavigationLink("Create Account") {
                    SignUpView()
                }
                NavigationLink("Forgot Password") {
                    ForgotPasswordView()
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
        .navigationTitle("Login")
    }
}
