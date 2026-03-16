import Foundation
import Combine

@MainActor
final class SupportCenterViewModel: ObservableObject {
    @Published private(set) var items: [SupportItemRecord] = []
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = AppConfigRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let fetched = try await repository.fetchGeneralSupportItems()
            let filtered = fetched.filter { $0.isActive ?? true }
            if filtered.isEmpty {
                items = defaultSupportItems()
                statusMessage = "Using in-app support defaults until cloud support items are configured."
            } else {
                items = ensureCoreSupportItems(in: filtered)
            }
        } catch {
            items = defaultSupportItems()
            statusMessage = "Cloud support items are unavailable. Using in-app defaults."
            errorMessage = nil
        }
    }

    private func defaultSupportItems() -> [SupportItemRecord] {
        [
            SupportItemRecord(id: "phone", title: "Contact Phone", value: "6143808069", type: "phone", isActive: true),
            SupportItemRecord(id: "email", title: "Support Email", value: "support@volunteersapp.com", type: "email", isActive: true),
            SupportItemRecord(id: "chat", title: "Live Chat", value: "Use AI Assistant for in-app help.", type: "chat", isActive: true)
        ]
    }

    private func ensureCoreSupportItems(in source: [SupportItemRecord]) -> [SupportItemRecord] {
        var out = source
        let hasPhone = source.contains { ($0.type ?? "").lowercased() == "phone" }
        let hasChat = source.contains {
            let type = ($0.type ?? "").lowercased()
            return type == "chat" || type == "live_chat"
        }
        if !hasPhone {
            out.append(
                SupportItemRecord(
                    id: "fallback_phone",
                    title: "Contact Phone",
                    value: "6143808069",
                    type: "phone",
                    isActive: true
                )
            )
        }
        if !hasChat {
            out.append(
                SupportItemRecord(
                    id: "fallback_chat",
                    title: "Live Chat",
                    value: "Use AI Assistant for in-app help.",
                    type: "chat",
                    isActive: true
                )
            )
        }
        return out
    }
}
