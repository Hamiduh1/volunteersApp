import Foundation
import Combine

enum VolunteerActivityStatusFilter: String, CaseIterable, Identifiable {
    case all
    case pending
    case approved
    case rejected
    case completed

    var id: String { rawValue }

    var title: String {
        rawValue.capitalized
    }
}

@MainActor
final class MyActivityViewModel: ObservableObject {
    @Published private(set) var items: [VolunteerActivityItem] = []
    @Published var selectedType: VolunteerActivityType = .all
    @Published var statusFilter: VolunteerActivityStatusFilter = .all
    @Published var query = ""
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = MyActivityRepository()

    var filteredItems: [VolunteerActivityItem] {
        let byType: [VolunteerActivityItem]
        switch selectedType {
        case .all:
            byType = items
        case .event:
            byType = items.filter { $0.type == .event }
        case .job:
            byType = items.filter { $0.type == .job }
        }

        let byStatus: [VolunteerActivityItem]
        switch statusFilter {
        case .all:
            byStatus = byType
        case .pending:
            byStatus = byType.filter { $0.status.isPendingLike }
        case .approved:
            byStatus = byType.filter { $0.status.isApprovedLike }
        case .rejected:
            byStatus = byType.filter { $0.status.isRejectedLike }
        case .completed:
            byStatus = byType.filter { $0.status == .completed || $0.status == .attended }
        }

        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return byStatus }
        return byStatus.filter { item in
            item.title.lowercased().contains(cleanQuery)
                || item.subtitle.lowercased().contains(cleanQuery)
                || item.referenceId.lowercased().contains(cleanQuery)
                || item.status.displayTitle.lowercased().contains(cleanQuery)
        }
    }

    func refresh(for user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchActivity(for: user.uid)
            statusMessage = items.isEmpty ? "No activity found." : "Loaded \(items.count) activity item(s)."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

