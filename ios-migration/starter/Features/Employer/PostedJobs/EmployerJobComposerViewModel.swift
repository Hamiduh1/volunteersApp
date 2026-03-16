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
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}
