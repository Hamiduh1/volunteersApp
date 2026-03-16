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
    @Published var selectedFilter: EmployerApplicationFilter = .all
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = EmployerRepository()

    var filteredItems: [EmployerManagedApplicationItem] {
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

    func approve(_ item: EmployerManagedApplicationItem, uid: String) async {
        await update(item, to: .approved, uid: uid)
    }

    func reject(_ item: EmployerManagedApplicationItem, uid: String) async {
        await update(item, to: .rejected, uid: uid)
    }

    private func update(_ item: EmployerManagedApplicationItem, to status: ApplicationStatus, uid: String) async {
        do {
            try await repository.updateApplicationStatus(applicationId: item.applicationId, status: status)
            await refresh(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
