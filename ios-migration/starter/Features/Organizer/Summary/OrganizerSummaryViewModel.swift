import Foundation
import Combine

@MainActor
final class OrganizerSummaryViewModel: ObservableObject {
    @Published private(set) var items: [OrganizerSummaryEventItem] = []
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OrganizerRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchSummaryItems(uid: uid)
            statusMessage = items.isEmpty
                ? "No hosted events yet."
                : "Loaded \(items.count) summary item(s)."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

