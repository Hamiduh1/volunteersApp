import Foundation
import Combine

enum OrganizerApplicationFilter: String, CaseIterable, Identifiable {
    case all
    case pending
    case approved
    case rejected

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
}

@MainActor
final class OrganizerApplicationsReviewViewModel: ObservableObject {
    @Published private(set) var items: [OrganizerManagedApplicationItem] = []
    @Published var selectedFilter: OrganizerApplicationFilter = .all
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = OrganizerRepository()

    var filteredItems: [OrganizerManagedApplicationItem] {
        switch selectedFilter {
        case .all:
            return items
        case .pending:
            return items.filter { $0.status == .pending || $0.status == .viewed }
        case .approved:
            return items.filter { $0.status == .approved || $0.status == .accepted || $0.status == .attended || $0.status == .completed }
        case .rejected:
            return items.filter { $0.status == .rejected || $0.status == .rejectedByEmployer || $0.status == .withdrawn }
        }
    }

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchManagedApplications(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func approve(_ item: OrganizerManagedApplicationItem, uid: String) async {
        await update(item, to: .approved, uid: uid)
    }

    func reject(_ item: OrganizerManagedApplicationItem, uid: String) async {
        await update(item, to: .rejected, uid: uid)
    }

    private func update(_ item: OrganizerManagedApplicationItem, to status: ApplicationStatus, uid: String) async {
        do {
            try await repository.updateApplicationStatus(eventId: item.eventId, documentId: item.documentId, status: status)
            await refresh(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
