import SwiftUI

struct SignUpView: View {
    @StateObject private var viewModel = SignUpViewModel()

    var body: some View {
        Form {
            Section("Create Account") {
                TextField("Full name", text: $viewModel.name)
                TextField("Username", text: $viewModel.username)
                    .textInputAutocapitalization(.never)
                TextField("Email", text: $viewModel.email)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
                SecureField("Password", text: $viewModel.password)
                SecureField("Confirm password", text: $viewModel.confirmPassword)
            }

            Section {
                Button(viewModel.isLoading ? "Creating account..." : "Create Account") {
                    Task { await viewModel.signUp() }
                }
                .disabled(viewModel.isLoading)
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
