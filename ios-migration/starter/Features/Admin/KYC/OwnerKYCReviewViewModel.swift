import Foundation
import Combine

@MainActor
final class OwnerKYCReviewViewModel: ObservableObject {
    @Published private(set) var items: [OwnerKYCRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchKycUsers()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
