import SwiftUI

struct ConversationsListView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = ConversationsListViewModel()

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Filters") {
                TextField("Search invitations or conversations", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                Picker("Invitation Status", selection: $viewModel.invitationFilter) {
                    ForEach(InvitationStatusFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                Text("Showing \(viewModel.filteredInvitations.count) invitation(s) - \(viewModel.filteredConversations.count) conversation(s)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            if !viewModel.filteredInvitations.isEmpty {
                Section("Invitations") {
                    ForEach(viewModel.filteredInvitations) { invite in
                        let invitationId = invite.id ?? (invite.senderId ?? "")
                        let isUpdating = viewModel.updatingInvitationIds.contains(invitationId)
                        VStack(alignment: .leading, spacing: 8) {
                            Text(invite.senderName ?? invite.inviterName ?? "Unknown Sender")
                                .font(.headline)

                            if let email = invite.senderEmail, !email.isEmpty {
                                Text(email)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            let source = (invite.source ?? "chat").replacingOccurrences(of: "_", with: " ")
                            let statusText = (invite.status ?? "pending").capitalized
                            Text("\(source.capitalized) - \(statusText)")
                                .font(.caption2)
                                .foregroundStyle(.secondary)

                            if (invite.status ?? "pending").lowercased() == "pending" {
                                HStack(spacing: 8) {
                                    Button("Accept") {
                                        Task { await viewModel.acceptInvitation(invite, user: user) }
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(isUpdating)

                                    Button("Decline") {
                                        Task { await viewModel.declineInvitation(invite, user: user) }
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(isUpdating)

                                    if isUpdating {
                                        ProgressView()
                                            .controlSize(.small)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }

            Section("Conversations") {
                if viewModel.isLoading && viewModel.filteredConversations.isEmpty {
                    ProgressView("Loading conversations...")
                } else if viewModel.filteredConversations.isEmpty {
                    Text("No conversations yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.filteredConversations) { conversation in
                        let conversationId = conversation.id ?? ""
                        NavigationLink {
                            ConversationDetailView(user: user, conversationId: conversationId)
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(conversation.lastMessage ?? conversation.lastMessageText ?? "No messages yet")
                                    .lineLimit(2)
                                    .font(.subheadline)

                                if let ts = conversation.lastMessageTimestamp?.dateValue() {
                                    Text(ts.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .disabled(conversationId.isEmpty)
                    }
                }
            }
        }
        .navigationTitle("Chat")
        .task { await viewModel.refresh(user: user) }
        .refreshable { await viewModel.refresh(user: user) }
        .navigationDestination(item: $viewModel.routeToConversation) { route in
            ConversationDetailView(user: user, conversationId: route.id)
        }
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
