import Foundation
import Combine

@MainActor
final class TermsAndConditionsViewModel: ObservableObject {
    @Published private(set) var record: AppConfigTextRecord?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = AppConfigRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            record = try await repository.fetchTermsAndConditions()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
