import Foundation
import Combine

enum EmployerJobsFilter: String, CaseIterable, Identifiable {
    case all
    case open
    case closed

    var id: String { rawValue }

    var title: String {
        rawValue.capitalized
    }
}

@MainActor
final class EmployerPostedJobsViewModel: ObservableObject {
    @Published private(set) var jobs: [JobRecord] = []
    @Published private(set) var managedApplications: [EmployerManagedApplicationItem] = []
    @Published var selectedFilter: EmployerJobsFilter = .all
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = EmployerRepository()

    var filteredJobs: [JobRecord] {
        switch selectedFilter {
        case .all:
            return jobs
        case .open:
            return jobs.filter { normalizedStatus($0.status) == "open" }
        case .closed:
            return jobs.filter { normalizedStatus($0.status) == "closed" }
        }
    }

    var totalJobs: Int { jobs.count }
    var openJobs: Int { jobs.filter { normalizedStatus($0.status) == "open" }.count }
    var closedJobs: Int { jobs.filter { normalizedStatus($0.status) == "closed" }.count }
    var totalApplications: Int { managedApplications.count }
    var pendingApplications: Int { managedApplications.filter { $0.status.isPendingLike }.count }

    func applicationsCount(for jobId: String) -> Int {
        managedApplications.filter { $0.jobId == jobId }.count
    }

    func pendingApplicationsCount(for jobId: String) -> Int {
        managedApplications.filter { $0.jobId == jobId && $0.status.isPendingLike }.count
    }

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let fetchedJobs = repository.fetchPostedJobs(uid: uid)
            async let fetchedApplications = repository.fetchManagedApplications(uid: uid)
            jobs = try await fetchedJobs
            managedApplications = try await fetchedApplications
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func deleteJob(jobId: String, uid: String) async {
        errorMessage = nil
        do {
            try await repository.deleteJobPosting(jobId: jobId)
            await refresh(uid: uid)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func toggleJobStatus(jobId: String, currentStatus: String?, uid: String) async {
        let normalized = normalizedStatus(currentStatus)
        let nextStatus = normalized == "closed" ? "open" : "closed"
        do {
            try await repository.toggleJobStatus(jobId: jobId, status: nextStatus)
            await refresh(uid: uid)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func normalizedStatus(_ raw: String?) -> String {
        let value = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return value == "closed" ? "closed" : "open"
    }
}
