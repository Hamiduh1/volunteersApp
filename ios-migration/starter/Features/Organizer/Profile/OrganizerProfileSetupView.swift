import SwiftUI

struct OrganizerProfileSetupView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = OrganizerProfileSetupViewModel()

    var body: some View {
        Form {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }
            }

            Section("Account") {
                TextField("Name", text: $viewModel.name)
                TextField("Email", text: $viewModel.email)
                    .disabled(true)
                    .foregroundStyle(.secondary)
            }

            Section("Organization") {
                TextField("Organization Name", text: $viewModel.organizationName)
                TextField("Location", text: $viewModel.location)
                TextField("Bio", text: $viewModel.bio, axis: .vertical)
                    .lineLimit(3...6)
            }

            Section {
                Button {
                    Task { await viewModel.save(uid: user.uid) }
                } label: {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Text("Save Organizer Profile")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isSaving)
            }
        }
        .navigationTitle("Organizer Profile")
        .task { await viewModel.refresh(uid: user.uid) }
        .refreshable { await viewModel.refresh(uid: user.uid) }
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

