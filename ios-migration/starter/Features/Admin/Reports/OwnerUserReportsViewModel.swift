import Foundation
import Combine

@MainActor
final class OwnerUserReportsViewModel: ObservableObject {
    @Published private(set) var reports: [OwnerUserReportRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            reports = try await repository.fetchUserReports()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
