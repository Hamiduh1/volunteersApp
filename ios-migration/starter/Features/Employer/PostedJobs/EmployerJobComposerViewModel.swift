import Foundation
import Combine

@MainActor
final class EmployerJobComposerViewModel: ObservableObject {
    @Published var organizationName = ""
    @Published var opportunityTitle = ""
    @Published var roleTitle = ""
    @Published var description = ""
    @Published var location = ""
    @Published var category = "Community"
    @Published var volunteersNeeded = "1"
    @Published var scheduledDate = Date().addingTimeInterval(24 * 3600)

    @Published var isSubmitting = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    private let repository = EmployerRepository()
    private let existingJobId: String?

    var isEditMode: Bool { existingJobId != nil }
    let categories = ["Technology", "Health", "Education", "Environment", "Community", "Animals", "Arts & Culture", "Seniors", "Other"]

    init(existingJob: JobRecord? = nil) {
        self.existingJobId = existingJob?.id
        self.organizationName = existingJob?.organizationName ?? existingJob?.employerName ?? ""
        self.opportunityTitle = existingJob?.title ?? ""
        self.roleTitle = existingJob?.jobTitle ?? ""
        self.description = existingJob?.description ?? ""
        self.location = existingJob?.locationName ?? existingJob?.locationString ?? ""
        self.category = existingJob?.category ?? "Community"
        let needed = existingJob?.volunteersNeeded ?? existingJob?.totalSlots ?? 1
        self.volunteersNeeded = String(max(needed, 1))
        self.scheduledDate = EmployerJobComposerViewModel.resolveScheduledDate(from: existingJob)
    }

    func create(uid: String) async -> Bool {
        let cleanOrganization = organizationName.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanOpportunityTitle = opportunityTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanRoleTitle = roleTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanLocation = location.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCategory = category.trimmingCharacters(in: .whitespacesAndNewlines)
        let needed = Int(volunteersNeeded.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0

        guard !cleanOpportunityTitle.isEmpty, !cleanDescription.isEmpty else {
            errorMessage = "Opportunity title and description are required."
            return false
        }
        guard !cleanLocation.isEmpty else {
            errorMessage = "Location is required."
            return false
        }
        guard !cleanCategory.isEmpty else {
            errorMessage = "Category is required."
            return false
        }
        guard needed > 0 else {
            errorMessage = "Volunteers needed must be at least 1."
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
                    organizationName: cleanOrganization,
                    opportunityTitle: cleanOpportunityTitle,
                    roleTitle: cleanRoleTitle,
                    description: cleanDescription,
                    location: cleanLocation,
                    category: cleanCategory,
                    volunteersNeeded: needed,
                    scheduledDate: scheduledDate
                )
                statusMessage = "Job updated."
            } else {
                _ = try await repository.createJobPosting(
                    uid: uid,
                    organizationName: cleanOrganization,
                    opportunityTitle: cleanOpportunityTitle,
                    roleTitle: cleanRoleTitle,
                    description: cleanDescription,
                    location: cleanLocation,
                    category: cleanCategory,
                    volunteersNeeded: needed,
                    scheduledDate: scheduledDate
                )
                statusMessage = "Job posted."
            }
            return true
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            return false
        }
    }

    private static func resolveScheduledDate(from job: JobRecord?) -> Date {
        guard let job else {
            return Date().addingTimeInterval(24 * 3600)
        }

        if let dateString = job.date?.trimmingCharacters(in: .whitespacesAndNewlines), !dateString.isEmpty {
            let merged = "\(dateString) \(job.time?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "09:00 AM")"
            let formatters: [DateFormatter] = {
                let fmt1 = DateFormatter()
                fmt1.dateFormat = "MM/dd/yyyy hh:mm a"
                let fmt2 = DateFormatter()
                fmt2.dateFormat = "M/d/yyyy h:mm a"
                let fmt3 = DateFormatter()
                fmt3.dateFormat = "yyyy-MM-dd HH:mm"
                return [fmt1, fmt2, fmt3]
            }()
            for formatter in formatters {
                if let parsed = formatter.date(from: merged) {
                    return parsed
                }
            }
        }

        if let deadline = job.applicationDeadline?.dateValue() {
            return deadline
        }
        if let posted = job.postedDate?.dateValue() {
            return posted
        }
        return Date().addingTimeInterval(24 * 3600)
    }
}

