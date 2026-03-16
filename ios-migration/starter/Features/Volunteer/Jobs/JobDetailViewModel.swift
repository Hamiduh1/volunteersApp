import Foundation
import Combine

@MainActor
final class JobDetailViewModel: ObservableObject {
    @Published private(set) var job: JobRecord?
    @Published private(set) var application: JobApplicationRecord?
    @Published var isApplying = false
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = JobsRepository()

    var isApplied: Bool {
        application != nil
    }

    func load(jobId: String, user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            job = try await repository.fetchJob(jobId: jobId)
            application = try await repository.fetchJobApplication(jobId: jobId, uid: user.uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func apply(jobId: String, user: AppSessionUser) async {
        guard !jobId.isEmpty else { return }
        guard !isApplied else { return }
        guard !isApplying else { return }
        isApplying = true
        defer { isApplying = false }
        do {
            try await repository.applyToJob(jobId: jobId, user: user)
            application = try await repository.fetchJobApplication(jobId: jobId, uid: user.uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
