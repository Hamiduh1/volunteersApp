import SwiftUI

struct ConversationsListView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = ConversationsListViewModel()

    var body: some View {
        List {
            if !viewModel.invitations.isEmpty {
                Section("Invitations") {
                    ForEach(viewModel.invitations) { invite in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(invite.senderName ?? "Unknown Sender")
                                .font(.headline)
                            if let email = invite.senderEmail, !email.isEmpty {
                                Text(email)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Text(invite.status ?? "pending")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }

            Section("Conversations") {
                if viewModel.isLoading && viewModel.conversations.isEmpty {
                    ProgressView("Loading conversations...")
                } else if viewModel.conversations.isEmpty {
                    Text("No conversations yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.conversations) { conversation in
                        let conversationId = conversation.id ?? ""
                        NavigationLink {
                            ConversationDetailView(user: user, conversationId: conversationId)
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(conversation.lastMessage ?? "No messages yet")
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
