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

    enum ApplicationActionState: Equatable {
        case canApply
        case appliedPending
        case approved
        case rejected
        case jobClosed
        case checking
    }

    var actionState: ApplicationActionState {
        if isApplying {
            return .checking
        }

        let normalizedJobStatus = (job?.status ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if normalizedJobStatus == "closed" || normalizedJobStatus == "completed" {
            return .jobClosed
        }

        guard let status = application?.status else {
            return .canApply
        }

        if status.isApprovedLike {
            return .approved
        }
        if status.isRejectedLike {
            return .rejected
        }
        return .appliedPending
    }

    func load(jobId: String, user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            job = try await repository.fetchJob(jobId: jobId)
            application = try await repository.fetchJobApplication(jobId: jobId, uid: user.uid)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func apply(jobId: String, user: AppSessionUser) async {
        guard !jobId.isEmpty else { return }
        guard actionState == .canApply else { return }
        guard !isApplying else { return }
        isApplying = true
        defer { isApplying = false }
        do {
            try await repository.applyToJob(jobId: jobId, user: user)
            application = try await repository.fetchJobApplication(jobId: jobId, uid: user.uid)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

