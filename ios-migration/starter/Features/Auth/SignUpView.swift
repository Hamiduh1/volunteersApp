import SwiftUI

struct SignUpView: View {
    @StateObject private var viewModel = SignUpViewModel()

    private var roleBinding: Binding<String> {
        Binding(
            get: { viewModel.selectedRole },
            set: { viewModel.selectedRole = $0 }
        )
    }

    private var countryBinding: Binding<String> {
        Binding(
            get: { viewModel.country },
            set: { viewModel.country = $0 }
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
                            size: 60,
                            useTemplate: false
                        )
                        BrandSymbolView(
                            assetName: BrandAsset.companyMark,
                            fallbackSystemName: "building.2.fill",
                            size: 30,
                            useTemplate: false
                        )
                    }
                    Text("Create Account")
                        .font(.title3.weight(.semibold))
                    Text("Set up your profile to open the right dashboard")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
            }

            Section("Dashboard Home") {
                Text("Registration supports Volunteer, Organizer, and Employer account dashboards.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Account Type") {
                Picker("Account type", selection: roleBinding) {
                    ForEach(viewModel.roleOptions) { option in
                        Text(option.label).tag(option.value)
                    }
                }
            }

            Section("Profile Details") {
                TextField("Full name", text: $viewModel.name)
                    .textContentType(.name)
                    .autocorrectionDisabled(true)
                TextField("Email", text: $viewModel.email)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                Picker("Country", selection: countryBinding) {
                    ForEach(viewModel.countryOptions, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
                TextField("Phone number", text: $viewModel.phoneNumber)
                    .keyboardType(.phonePad)

                if viewModel.requiresCompanyName {
                    TextField(
                        viewModel.selectedRole == "organizer" ? "Organization name" : "Company name",
                        text: $viewModel.companyName
                    )
                }
                if viewModel.requiresAgeVerification {
                    DatePicker(
                        "Date of birth (18+)",
                        selection: $viewModel.birthDate,
                        in: ...Date(),
                        displayedComponents: .date
                    )
                }

                SecureField("Password", text: $viewModel.password)
                    .textContentType(.newPassword)
                SecureField("Confirm password", text: $viewModel.confirmPassword)
                    .textContentType(.newPassword)
            }

            Section {
                Button(viewModel.isLoading ? "Creating account..." : "Create Account") {
                    Task { await viewModel.signUp() }
                }
                .disabled(viewModel.isLoading)
            }

            if let statusMessage = viewModel.statusMessage {
                Section("Status") {
                    Text(statusMessage).foregroundStyle(.green)
                    NavigationLink("Enter verification code") {
                        EmailVerificationView(prefilledEmail: viewModel.email)
                    }
                }
            }

            Section("Navigation") {
                NavigationLink("Back to Login") {
                    LoginView()
                }
                NavigationLink("Forgot Password") {
                    ForgotPasswordView()
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section("Error") {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Sign Up")
    }
}
