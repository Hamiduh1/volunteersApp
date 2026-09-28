import SwiftUI

struct AccountSecurityView: View {
    let email: String
    @StateObject private var viewModel = AccountSecurityViewModel()

    var body: some View {
        Form {
            Section("Account") {
                Text(email.isEmpty ? "No email on file" : email)
                    .foregroundStyle(.secondary)
            }

            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }
            }

            Section("Security Actions") {
                Button {
                    Task { await viewModel.sendPasswordReset(email: email) }
                } label: {
                    if viewModel.isSendingReset {
                        ProgressView()
                    } else {
                        Text("Send Password Reset Email")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isSendingReset || email.isEmpty)
            }
        }
        .navigationTitle("Security Center")
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }
}
