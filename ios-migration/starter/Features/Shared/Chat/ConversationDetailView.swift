import SwiftUI

struct ConversationDetailView: View {
    let user: AppSessionUser
    let conversationId: String
    @StateObject private var viewModel = ConversationDetailViewModel()

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.isLoading && viewModel.messages.isEmpty {
                Spacer()
                ProgressView("Loading messages...")
                Spacer()
            } else if viewModel.messages.isEmpty {
                Spacer()
                Text("No messages yet.")
                    .foregroundStyle(.secondary)
                Spacer()
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            ForEach(viewModel.messages) { message in
                                let isMine = message.senderId == user.uid
                                HStack {
                                    if isMine { Spacer(minLength: 32) }
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(message.text ?? "")
                                            .font(.body)
                                        if let time = message.timestamp?.dateValue() {
                                            Text(time.formatted(date: .omitted, time: .shortened))
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    .padding(10)
                                    .background(isMine ? Color.blue.opacity(0.15) : Color.gray.opacity(0.15))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    if !isMine { Spacer(minLength: 32) }
                                }
                                .id(message.id ?? UUID().uuidString)
                            }
                        }
                        .padding()
                    }
                    .onChange(of: viewModel.messages.count) { _, _ in
                        if let lastId = viewModel.messages.last?.id {
                            withAnimation {
                                proxy.scrollTo(lastId, anchor: .bottom)
                            }
                        }
                    }
                }
            }

            Divider()

            HStack(spacing: 10) {
                TextField("Type a message...", text: $viewModel.composerText, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(1...4)

                Button("Send") {
                    Task { await viewModel.send(user: user, conversationId: conversationId) }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.composerText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isSending)
            }
            .padding()
        }
        .navigationTitle("Conversation")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.refresh(conversationId: conversationId) }
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
