import Foundation
import Combine

@MainActor
final class ConversationDetailViewModel: ObservableObject {
    @Published private(set) var messages: [ChatMessageRecord] = []
    @Published var composerText = ""
    @Published var isLoading = false
    @Published var isSending = false
    @Published var errorMessage: String?

    private let repository = ChatRepository()

    func refresh(conversationId: String) async {
        guard !conversationId.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            messages = try await repository.fetchMessages(conversationId: conversationId)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func send(user: AppSessionUser, conversationId: String) async {
        let text = composerText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        guard !conversationId.isEmpty else { return }

        isSending = true
        defer { isSending = false }

        do {
            try await repository.sendMessage(conversationId: conversationId, senderId: user.uid, text: text)
            composerText = ""
            await refresh(conversationId: conversationId)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

