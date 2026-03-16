import Foundation
import Combine

@MainActor
final class OrganizerEventComposerViewModel: ObservableObject {
    @Published var title = ""
    @Published var description = ""
    @Published var category = "Community"
    @Published var locationName = ""
    @Published var locationAddress = ""
    @Published var eventDate = Date().addingTimeInterval(3600)
    @Published var volunteerLimitText = "20"
    @Published var paymentText = "0"

    @Published var isSubmitting = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    private let repository = OrganizerRepository()
    private let existingEventId: String?

    var isEditMode: Bool { existingEventId != nil }

    init(existingEvent: EventRecord? = nil) {
        self.existingEventId = existingEvent?.id
        self.title = existingEvent?.title ?? ""
        self.description = existingEvent?.description ?? ""
        self.category = existingEvent?.category ?? "Community"
        self.locationName = existingEvent?.locationName ?? ""
        self.locationAddress = existingEvent?.locationAddress ?? ""
        self.eventDate = existingEvent?.eventDateTime?.dateValue() ?? Date().addingTimeInterval(3600)
        if let limit = existingEvent?.volunteerLimit, limit > 0 {
            self.volunteerLimitText = String(limit)
        }
        let fee = existingEvent?.payment ?? existingEvent?.eventFee ?? 0
        self.paymentText = String(format: "%.2f", fee)
    }

    func create(uid: String) async -> Bool {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanLocation = locationName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty, !cleanDescription.isEmpty, !cleanLocation.isEmpty else {
            errorMessage = "Title, description, and location are required."
            return false
        }

        guard let volunteerLimit = Int(volunteerLimitText), volunteerLimit > 0 else {
            errorMessage = "Volunteer limit must be a positive number."
            return false
        }
        guard let payment = Double(paymentText), payment >= 0 else {
            errorMessage = "Payment must be a valid number."
            return false
        }

        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        do {
            if let existingEventId, !existingEventId.isEmpty {
                try await repository.updateHostedEvent(
                    eventId: existingEventId,
                    uid: uid,
                    title: cleanTitle,
                    description: cleanDescription,
                    category: category,
                    locationName: cleanLocation,
                    locationAddress: locationAddress,
                    eventDate: eventDate,
                    volunteerLimit: volunteerLimit,
                    payment: payment
                )
                statusMessage = "Event updated."
            } else {
                _ = try await repository.createHostedEvent(
                    uid: uid,
                    title: cleanTitle,
                    description: cleanDescription,
                    category: category,
                    locationName: cleanLocation,
                    locationAddress: locationAddress,
                    eventDate: eventDate,
                    volunteerLimit: volunteerLimit,
                    payment: payment
                )
                statusMessage = "Event created."
            }
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}
