import Foundation
import Combine

@MainActor
final class EmployerJobComposerViewModel: ObservableObject {
    @Published var title = ""
    @Published var description = ""
    @Published var locationString = ""
    @Published var category = "General"
    @Published var jobType = "Part-time"
    @Published var salaryOrCompensation = ""
    @Published var applicationDeadline = Date().addingTimeInterval(7 * 24 * 3600)

    @Published var isSubmitting = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    private let repository = EmployerRepository()
    private let existingJobId: String?

    var isEditMode: Bool { existingJobId != nil }

    init(existingJob: JobRecord? = nil) {
        self.existingJobId = existingJob?.id
        self.title = existingJob?.title ?? ""
        self.description = existingJob?.description ?? ""
        self.locationString = existingJob?.locationString ?? ""
        self.category = existingJob?.category ?? "General"
        self.jobType = existingJob?.jobType ?? "Part-time"
        self.salaryOrCompensation = existingJob?.salaryOrCompensation ?? ""
        self.applicationDeadline = existingJob?.applicationDeadline?.dateValue()
            ?? Date().addingTimeInterval(7 * 24 * 3600)
    }

    func create(uid: String) async -> Bool {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty, !cleanDescription.isEmpty else {
            errorMessage = "Title and description are required."
            return false
        }

        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        do {
            if let existingJobId, !existingJobId.isEmpty {
                try await repository.updateJobPosting(
                    jobId: existingJobId,
                    uid: uid,
                    title: cleanTitle,
                    description: cleanDescription,
                    locationString: locationString,
                    category: category,
                    jobType: jobType,
                    salaryOrCompensation: salaryOrCompensation,
                    applicationDeadline: applicationDeadline
                )
                statusMessage = "Job updated."
            } else {
                _ = try await repository.createJobPosting(
                    uid: uid,
                    title: cleanTitle,
                    description: cleanDescription,
                    locationString: locationString,
                    category: category,
                    jobType: jobType,
                    salaryOrCompensation: salaryOrCompensation,
                    applicationDeadline: applicationDeadline
                )
                statusMessage = "Job posted."
            }
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}
