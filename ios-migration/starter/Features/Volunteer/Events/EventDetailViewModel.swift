import Foundation
import Combine

@MainActor
final class EventDetailViewModel: ObservableObject {
    @Published private(set) var event: EventRecord?
    @Published private(set) var application: EventApplicationRecord?
    @Published var isApplying = false
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = EventsRepository()

    var isApplied: Bool {
        application != nil
    }

    func load(eventId: String, user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            event = try await repository.fetchEvent(eventId: eventId)
            application = try await repository.fetchEventApplication(eventId: eventId, uid: user.uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func apply(eventId: String, user: AppSessionUser) async {
        guard !eventId.isEmpty else { return }
        guard !isApplied else { return }
        guard !isApplying else { return }
        isApplying = true
        defer { isApplying = false }
        do {
            try await repository.applyToEvent(eventId: eventId, user: user)
            application = try await repository.fetchEventApplication(eventId: eventId, uid: user.uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
