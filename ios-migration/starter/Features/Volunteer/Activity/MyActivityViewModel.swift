import Foundation
import Combine

@MainActor
final class MyActivityViewModel: ObservableObject {
    @Published private(set) var items: [VolunteerActivityItem] = []
    @Published var selectedType: VolunteerActivityType = .all
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = MyActivityRepository()

    var filteredItems: [VolunteerActivityItem] {
        switch selectedType {
        case .all:
            return items
        case .event:
            return items.filter { $0.type == .event }
        case .job:
            return items.filter { $0.type == .job }
        }
    }

    func refresh(for user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchActivity(for: user.uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
