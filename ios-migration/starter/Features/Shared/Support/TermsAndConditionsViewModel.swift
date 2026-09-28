import Foundation
import Combine

@MainActor
final class TermsAndConditionsViewModel: ObservableObject {
    @Published private(set) var record: AppConfigTextRecord?
    @Published var isLoading = false
    @Published var noticeMessage: String?
    @Published var errorMessage: String?

    private let repository = AppConfigRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        noticeMessage = nil
        defer { isLoading = false }

        do {
            let fetched = try await repository.fetchTermsAndConditions()
            if (fetched.content ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                record = Self.defaultTermsRecord()
                noticeMessage = "Using in-app terms template until cloud content is available."
            } else {
                record = fetched
            }
        } catch {
            record = Self.defaultTermsRecord()
            noticeMessage = "Cloud terms are unavailable. Using in-app template."
            errorMessage = nil
        }
    }

    private static func defaultTermsRecord() -> AppConfigTextRecord {
        AppConfigTextRecord(
            title: "Terms and Conditions",
            content: """
            By using Volunteers App, users agree to provide accurate account information and follow community, job, event, and payment rules.
            Organizers and employers are responsible for lawful postings, clear requirements, and fair application decisions.
            Users must not misuse messaging, live features, wallet tools, or upload prohibited content.
            The platform may suspend accounts for abuse, fraud, or violations of applicable law and service policies.
            """,
            updatedAt: nil
        )
    }
}
