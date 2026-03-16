import Foundation
import Combine

@MainActor
final class EmployerPostedJobsViewModel: ObservableObject {
    @Published private(set) var jobs: [JobRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = EmployerRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            jobs = try await repository.fetchPostedJobs(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
