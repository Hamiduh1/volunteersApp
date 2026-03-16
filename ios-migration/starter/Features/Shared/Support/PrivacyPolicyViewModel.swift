import Foundation
import Combine

@MainActor
final class PrivacyPolicyViewModel: ObservableObject {
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
            let fetched = try await repository.fetchPrivacyPolicy()
            if (fetched.content ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                record = Self.defaultPrivacyPolicyRecord()
                noticeMessage = "Using in-app privacy template until cloud content is available."
            } else {
                record = fetched
            }
        } catch {
            record = Self.defaultPrivacyPolicyRecord()
            noticeMessage = "Cloud privacy policy is unavailable. Using in-app template."
            errorMessage = nil
        }
    }

    private static func defaultPrivacyPolicyRecord() -> AppConfigTextRecord {
        AppConfigTextRecord(
            title: "Privacy Policy",
            content: """
            Volunteers App collects only the data needed to provide account access, matching, messaging, and wallet features.
            We use profile data, activity records, and transaction metadata to run core app functionality and support requests.
            Sensitive operations such as payments and verification are handled through secure providers and role-based access controls.
            We do not sell personal information, and users can request updates or deletion of profile data through support.
            """,
            updatedAt: nil
        )
    }
}
