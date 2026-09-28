import Foundation
import Combine

@MainActor
final class EventsListViewModel: ObservableObject {
    @Published private(set) var events: [EventRecord] = []
    @Published private(set) var appliedEventIds: Set<String> = []
    @Published private(set) var applyInFlightEventIds: Set<String> = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = EventsRepository()

    func refresh(for user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let allEvents = try await repository.fetchEvents()
            events = allEvents.sorted { lhs, rhs in
                let l = lhs.eventDateTime?.dateValue() ?? .distantFuture
                let r = rhs.eventDateTime?.dateValue() ?? .distantFuture
                return l < r
            }

            var applied = Set<String>()
            for event in events {
                let eventId = event.id ?? ""
                if eventId.isEmpty { continue }
                if try await repository.hasApplied(eventId: eventId, uid: user.uid) {
                    applied.insert(eventId)
                }
            }
            appliedEventIds = applied
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func apply(eventId: String, user: AppSessionUser) async {
        guard !eventId.isEmpty else { return }
        guard !appliedEventIds.contains(eventId) else { return }
        guard !applyInFlightEventIds.contains(eventId) else { return }
        applyInFlightEventIds.insert(eventId)
        defer { applyInFlightEventIds.remove(eventId) }
        do {
            try await repository.applyToEvent(eventId: eventId, user: user)
            appliedEventIds.insert(eventId)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

