import Foundation
import Combine

@MainActor
final class PrivacyPolicyViewModel: ObservableObject {
    @Published private(set) var record: AppConfigTextRecord?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = AppConfigRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            record = try await repository.fetchPrivacyPolicy()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
