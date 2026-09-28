import Foundation
import Combine

enum EmployerApplicationFilter: String, CaseIterable, Identifiable {
    case all
    case pending
    case approved
    case rejected

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
}

@MainActor
final class EmployerApplicationsReviewViewModel: ObservableObject {
    @Published private(set) var items: [EmployerManagedApplicationItem] = []
    @Published private(set) var currentJobId: String?
    @Published var selectedFilter: EmployerApplicationFilter = .all
    @Published var isLoading = false
    @Published private(set) var updatingIds: Set<String> = []
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = EmployerRepository()

    var filteredItems: [EmployerManagedApplicationItem] {
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

    func refresh(uid: String, jobId: String? = nil) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        currentJobId = jobId
        defer { isLoading = false }
        do {
            items = try await repository.fetchManagedApplications(uid: uid, jobId: jobId)
            statusMessage = items.isEmpty ? "No applications found." : "Loaded \(items.count) applications."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func approve(_ item: EmployerManagedApplicationItem, uid: String) async {
        await update(item, to: .approved, uid: uid)
    }

    func reject(_ item: EmployerManagedApplicationItem, uid: String) async {
        await update(item, to: .rejected, uid: uid)
    }

    private func update(_ item: EmployerManagedApplicationItem, to status: ApplicationStatus, uid: String) async {
        guard item.status != status else { return }
        guard item.status.isPendingLike else {
            statusMessage = "Only pending applications can be updated."
            return
        }

        updatingIds.insert(item.id)
        defer { updatingIds.remove(item.id) }
        do {
            try await repository.updateApplicationStatus(item: item, status: status)
            statusMessage = "Application marked as \(status.displayTitle)."
            await refresh(uid: uid, jobId: currentJobId)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

