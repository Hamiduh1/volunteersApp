import Foundation
import Combine

@MainActor
final class SupportCenterViewModel: ObservableObject {
    @Published private(set) var items: [SupportItemRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = AppConfigRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let fetched = try await repository.fetchGeneralSupportItems()
            let filtered = fetched.filter { $0.isActive ?? true }
            items = filtered.isEmpty ? defaultSupportItems() : filtered
        } catch {
            items = defaultSupportItems()
            errorMessage = error.localizedDescription
        }
    }

    private func defaultSupportItems() -> [SupportItemRecord] {
        [
            SupportItemRecord(id: "phone", title: "Contact Phone", value: "6143808069", type: "phone", isActive: true),
            SupportItemRecord(id: "email", title: "Support Email", value: "support@volunteersapp.com", type: "email", isActive: true),
            SupportItemRecord(id: "chat", title: "Live Chat", value: "Use AI Assistant for in-app help.", type: "text", isActive: true)
        ]
    }
}
