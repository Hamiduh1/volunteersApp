import Foundation
import Combine

@MainActor
final class OrganizerHostedEventsViewModel: ObservableObject {
    @Published private(set) var events: [EventRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = OrganizerRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            events = try await repository.fetchHostedEvents(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
