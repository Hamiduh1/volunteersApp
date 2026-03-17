import SwiftUI

struct UserDirectoryView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = UserDirectoryViewModel()

    var body: some View {
        List {
            Section {
                TextField("Search by name, email, username, phone", text: $viewModel.searchQuery)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Text("Showing \(viewModel.filteredUsers.count) user(s)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Directory") {
                if viewModel.isLoading && viewModel.filteredUsers.isEmpty {
                    ProgressView("Loading users...")
                } else if viewModel.filteredUsers.isEmpty {
                    emptyState
                } else {
                    ForEach(viewModel.filteredUsers) { directoryUser in
                        directoryRow(directoryUser)
                    }
                }
            }
        }
        .navigationTitle("Find People")
        .task { await viewModel.refresh(currentUser: user) }
        .refreshable { await viewModel.refresh(currentUser: user) }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "person.2")
                .font(.title2)
                .foregroundStyle(.secondary)
            let cleanQuery = viewModel.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            Text(cleanQuery.isEmpty ? "No users available." : "No one found matching \"\(cleanQuery)\"")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 120)
        .padding(.vertical, 6)
    }

    @ViewBuilder
    private func directoryRow(_ directoryUser: DirectoryUserRecord) -> some View {
        let isSent = viewModel.isInvitationSent(for: directoryUser.uid)
        let isSending = viewModel.isSendingInvitation(for: directoryUser.uid)

        HStack(spacing: 12) {
            avatar(urlString: directoryUser.profileImageUrl)

            VStack(alignment: .leading, spacing: 3) {
                Text(directoryUser.name)
                    .font(.headline)
                    .lineLimit(1)
                if !directoryUser.email.isEmpty {
                    Text(directoryUser.email)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                } else if !directoryUser.username.isEmpty {
                    Text("@\(directoryUser.username)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                } else if !directoryUser.phoneNumber.isEmpty {
                    Text(directoryUser.phoneNumber)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            Button {
                Task { await viewModel.sendInvitation(to: directoryUser, currentUser: user) }
            } label: {
                if isSending {
                    HStack(spacing: 6) {
                        ProgressView()
                            .controlSize(.small)
                        Text("Sending...")
                    }
                } else {
                    Label(isSent ? "Sent" : "Invite", systemImage: isSent ? "checkmark" : "paperplane")
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isSent || isSending)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func avatar(urlString: String?) -> some View {
        if let urlString, let url = URL(string: urlString), !urlString.isEmpty {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                default:
                    Image(systemName: "person.fill")
                        .resizable()
                        .scaledToFit()
                        .padding(6)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 44, height: 44)
            .clipShape(Circle())
        } else {
            Image(systemName: "person.fill")
                .resizable()
                .scaledToFit()
                .padding(6)
                .frame(width: 44, height: 44)
                .foregroundStyle(.secondary)
                .background(Circle().fill(Color(uiColor: .tertiarySystemFill)))
        }
    }
}
