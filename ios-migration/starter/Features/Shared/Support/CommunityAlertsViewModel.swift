import Foundation

@MainActor
final class CommunityAlertsViewModel: ObservableObject {
    @Published private(set) var alerts: [CommunityAlertRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    private let uid: String
    private let repository = AlertsRepository()

    init(uid: String) {
        self.uid = uid
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            alerts = try await repository.fetchCommunityAlerts(uid: uid)
            if alerts.isEmpty {
                statusMessage = "No community alerts yet."
            }
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
