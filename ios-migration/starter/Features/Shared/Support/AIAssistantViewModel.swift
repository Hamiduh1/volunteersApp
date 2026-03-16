import Foundation
import Combine

struct AIAssistantMessage: Identifiable {
    let id = UUID()
    let role: String // user | assistant
    let text: String
    let createdAt: Date
}

@MainActor
final class AIAssistantViewModel: ObservableObject {
    @Published private(set) var faqItems: [FAQItemRecord] = []
    @Published private(set) var messages: [AIAssistantMessage] = []
    @Published var query = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = AppConfigRepository()

    func loadFaq() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            faqItems = try await repository.fetchFaq()
            if messages.isEmpty {
                messages.append(
                    AIAssistantMessage(
                        role: "assistant",
                        text: "Hi! Ask about events, jobs, wallet, calls, or account setup.",
                        createdAt: Date()
                    )
                )
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func useSuggestion(_ item: FAQItemRecord) {
        query = item.question ?? ""
    }

    func send() {
        let prompt = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty else { return }

        messages.append(AIAssistantMessage(role: "user", text: prompt, createdAt: Date()))
        let response = respond(to: prompt)
        messages.append(AIAssistantMessage(role: "assistant", text: response, createdAt: Date()))
        query = ""
    }

    private func respond(to prompt: String) -> String {
        let normalized = prompt.lowercased()

        if let match = faqItems.first(where: { item in
            let q = (item.question ?? "").lowercased()
            let tags = item.tags?.map { $0.lowercased() } ?? []
            return q.contains(normalized) || normalized.contains(q) || tags.contains(where: { normalized.contains($0) })
        }) {
            return match.answer ?? "I found a matching FAQ, but it has no answer text yet."
        }

        if normalized.contains("wallet") {
            return "Open Wallet from your role home tab to view balance, transactions, and payout setup."
        }
        if normalized.contains("job") {
            return "Use Jobs to browse opportunities, apply once per role rules, and track status in Activity."
        }
        if normalized.contains("event") {
            return "Use Events to browse and apply, then check Activity for approval or rejection updates."
        }
        if normalized.contains("call") || normalized.contains("video") {
            return "Use Calls for history and Live for streams. If media access fails, check Firestore/Storage rules."
        }

        return "I do not have a direct in-app answer yet. Try Privacy, Terms, or Support Center from Tools."
    }
}
