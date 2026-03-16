import Foundation
import Combine

@MainActor
final class JobsListViewModel: ObservableObject {
    @Published private(set) var jobs: [JobRecord] = []
    @Published private(set) var appliedJobIds: Set<String> = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = JobsRepository()

    func refresh(for user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let allJobs = try await repository.fetchJobs()
            jobs = allJobs

            var applied = Set<String>()
            for job in jobs {
                let jobId = job.id ?? ""
                if jobId.isEmpty { continue }
                if try await repository.hasApplied(jobId: jobId, uid: user.uid) {
                    applied.insert(jobId)
                }
            }
            appliedJobIds = applied
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func apply(jobId: String, user: AppSessionUser) async {
        guard !appliedJobIds.contains(jobId) else { return }
        do {
            try await repository.applyToJob(jobId: jobId, user: user)
            appliedJobIds.insert(jobId)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
