import Foundation
import Combine

@MainActor
final class OrganizerDashboardViewModel: ObservableObject {
    @Published private(set) var snapshot = OrganizerDashboardSnapshot(
        organizerName: "Organizer",
        canGoLive: false,
        eventCount: 0,
        totalVolunteers: 0,
        totalEarnings: 0
    )
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = OrganizerRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            snapshot = try await repository.fetchDashboardSnapshot(uid: uid)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

