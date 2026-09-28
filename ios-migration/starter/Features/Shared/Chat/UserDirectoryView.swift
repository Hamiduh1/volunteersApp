import SwiftUI

struct UserDirectoryView: View {
    private enum SectionAnchor {
        static let dashboard = "directory_dashboard"
        static let search = "directory_search"
        static let list = "directory_list"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = UserDirectoryViewModel()

    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    dashboardHomeSection(proxy: proxy)
                }
                .id(SectionAnchor.dashboard)

                Section {
                    TextField("Search by name, email, username, phone", text: $viewModel.searchQuery)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Text("Showing \(viewModel.filteredUsers.count) user(s)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .id(SectionAnchor.search)

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
                .id(SectionAnchor.list)
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
    private func dashboardHomeSection(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Dashboard Home")
                .font(.headline)

            Text("Android-style user directory navigation with invite workflow controls.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                dashboardMetric(value: "\(viewModel.allUsers.count)", label: "Users")
                dashboardMetric(value: "\(viewModel.filteredUsers.count)", label: "Visible")
                dashboardMetric(value: "\(viewModel.sentInvitationUserIds.count)", label: "Invites Sent")
            }

            HStack(spacing: 10) {
                dashboardTile(title: "Refresh", icon: "arrow.clockwise.circle.fill") {
                    Task { await viewModel.refresh(currentUser: user) }
                }
                dashboardTile(title: "Clear Search", icon: "xmark.circle.fill") {
                    viewModel.searchQuery = ""
                }
                NavigationLink {
                    ConversationsListView(user: user)
                } label: {
                    dashboardTileLabel(title: "Social Inbox", icon: "bubble.left.and.bubble.right.fill")
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 8) {
                dashboardQuickButton("Search") {
                    withAnimation { proxy.scrollTo(SectionAnchor.search, anchor: .top) }
                }
                dashboardQuickButton("Directory") {
                    withAnimation { proxy.scrollTo(SectionAnchor.list, anchor: .top) }
                }
            }

            HStack(spacing: 8) {
                NavigationLink {
                    CommunityHubView(user: user)
                } label: {
                    Label("Community Dashboard", systemImage: "square.grid.2x2.fill")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)

                NavigationLink {
                    AIAssistantView()
                } label: {
                    Label("AI Assistant", systemImage: "sparkles")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)
            }
        }
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardTile(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            dashboardTileLabel(title: title, icon: icon)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func dashboardTileLabel(title: String, icon: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.blue)
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardQuickButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .buttonStyle(.bordered)
            .font(.caption.weight(.semibold))
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
