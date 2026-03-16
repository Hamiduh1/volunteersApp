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
    @Published private(set) var updatingIds: Set<String> = []
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OrganizerRepository()

    var filteredItems: [OrganizerManagedApplicationItem] {
        switch selectedFilter {
        case .all:
            return items
        case .pending:
            return items.filter { $0.status.isPendingLike }
        case .approved:
            return items.filter { $0.status.isApprovedLike }
        case .rejected:
            return items.filter { $0.status.isRejectedLike }
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
        guard item.status != status else { return }
        guard item.status.isPendingLike else {
            statusMessage = "Only pending applications can be updated."
            return
        }

        updatingIds.insert(item.id)
        defer { updatingIds.remove(item.id) }
        do {
            try await repository.updateApplicationStatus(eventId: item.eventId, documentId: item.documentId, status: status)
            statusMessage = "Application marked as \(status.displayTitle)."
            await refresh(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
