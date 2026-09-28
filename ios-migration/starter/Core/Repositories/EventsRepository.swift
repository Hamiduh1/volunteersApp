import Foundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseFirestoreSwift

final class EventsRepository {
    private let db = Firestore.firestore()

    func fetchEvents(limit: Int = 50) async throws -> [EventRecord] {
        let snapshot = try await db.collection(FirestoreCollection.events.rawValue)
            .limit(to: max(limit * 3, 150))
            .getDocuments()

        let now = Date().addingTimeInterval(-60 * 60 * 2)
        let allowedStatuses: Set<String> = ["OPEN", "UPCOMING", "ACTIVE", "PUBLISHED"]
        let filtered = snapshot.documents.compactMap { doc -> EventRecord? in
            let data = doc.data()
            if (data["isDeleted"] as? Bool) == true { return nil }
            if let isActive = data["isActive"] as? Bool, !isActive { return nil }
            if let closeEntries = data["closeEntries"] as? Bool, closeEntries { return nil }
            let status = (data["status"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
            if status == "CANCELLED" || status == "CLOSED" || status == "ARCHIVED" {
                return nil
            }
            if !status.isEmpty && !allowedStatuses.contains(status) {
                return nil
            }
            guard let event = try? doc.data(as: EventRecord.self) else { return nil }
            if let eventDate = event.eventDateTime?.dateValue(), eventDate < now {
                return nil
            }
            return event
        }

        return filtered.sorted {
            let l = $0.eventDateTime?.dateValue() ?? .distantFuture
            let r = $1.eventDateTime?.dateValue() ?? .distantFuture
            return l < r
        }
        .prefix(limit)
        .map { $0 }
    }

    func hasApplied(eventId: String, uid: String) async throws -> Bool {
        let normalizedEventId = normalizeDocumentId(eventId)
        guard !normalizedEventId.isEmpty else { return false }
        let snap = try await db.collection(FirestoreCollection.events.rawValue)
            .document(normalizedEventId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        return snap.exists
    }

    func fetchEvent(eventId: String) async throws -> EventRecord? {
        let normalizedEventId = normalizeDocumentId(eventId)
        guard !normalizedEventId.isEmpty else { return nil }
        let snapshot = try await db.collection(FirestoreCollection.events.rawValue)
            .document(normalizedEventId)
            .getDocument()
        return try? snapshot.data(as: EventRecord.self)
    }

    func applyToEvent(eventId: String, user: AppSessionUser, userName: String? = nil) async throws {
        let normalizedEventId = normalizeDocumentId(eventId)
        guard !normalizedEventId.isEmpty else { return }

        let eventRef = db.collection(FirestoreCollection.events.rawValue).document(normalizedEventId)
        let eventDoc = try await eventRef.getDocument()
        let eventData = eventDoc.data() ?? [:]

        let payload: [String: Any] = [
            "applicationId": user.uid,
            "eventId": normalizedEventId,
            "eventTitle": (eventData["title"] as? String) ?? "",
            "userId": user.uid,
            "volunteerUid": user.uid,
            "volunteerId": user.uid,
            "volunteerName": userName ?? "",
            "volunteerEmail": user.email ?? "",
            "organizerId": (eventData["organizerId"] as? String) ?? "",
            "organizerUid": (eventData["organizerUid"] as? String) ?? ((eventData["organizerId"] as? String) ?? ""),
            "status": "PENDING",
            "appliedAt": FieldValue.serverTimestamp(),
            "appliedDate": FieldValue.serverTimestamp(),
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ]

        try await eventRef
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(user.uid)
            .setData(payload, merge: true)

        let rootId = "\(normalizedEventId)_\(user.uid)"
        try? await db.collection(FirestoreCollection.applications.rawValue)
            .document(rootId)
            .setData(payload, merge: true)
    }

    func fetchEventApplication(eventId: String, uid: String) async throws -> EventApplicationRecord? {
        let normalizedEventId = normalizeDocumentId(eventId)
        guard !normalizedEventId.isEmpty else { return nil }
        let snap = try await db.collection(FirestoreCollection.events.rawValue)
            .document(normalizedEventId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        guard snap.exists else { return nil }
        if let decoded = try? snap.data(as: EventApplicationRecord.self) {
            return decoded
        }

        let data = snap.data() ?? [:]
        return EventApplicationRecord(
            id: snap.documentID,
            applicationId: data.firstNonEmptyString(keys: ["applicationId", "id"]) ?? snap.documentID,
            eventId: data.firstNonEmptyString(keys: ["eventId"]) ?? normalizedEventId,
            volunteerId: data.firstNonEmptyString(keys: ["volunteerId", "userId", "volunteerUid"]),
            volunteerUid: data.firstNonEmptyString(keys: ["volunteerUid", "userId", "volunteerId"]),
            userId: data.firstNonEmptyString(keys: ["userId", "volunteerUid", "volunteerId"]),
            volunteerName: data.firstNonEmptyString(keys: ["volunteerName", "name"]),
            volunteerEmail: data.firstNonEmptyString(keys: ["volunteerEmail", "email"]),
            organizerId: data.firstNonEmptyString(keys: ["organizerId"]),
            organizerUid: data.firstNonEmptyString(keys: ["organizerUid", "organizerId"]),
            status: ApplicationStatus(rawValue: (data.firstNonEmptyString(keys: ["status"]) ?? "UNKNOWN").uppercased()) ?? .unknown,
            appliedAt: data.firstTimestamp(keys: ["appliedAt", "appliedDate", "createdAt", "timestamp"]),
            appliedDate: data.firstTimestamp(keys: ["appliedDate", "appliedAt", "createdAt", "timestamp"]),
            lastUpdatedAt: data.firstTimestamp(keys: ["lastUpdatedAt", "updatedAt"])
        )
    }

    private func normalizeDocumentId(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let segments = trimmed.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        guard !segments.isEmpty else { return "" }
        if let index = segments.firstIndex(of: FirestoreCollection.events.rawValue), segments.count > index + 1 {
            return segments[index + 1]
        }
        return segments.last ?? trimmed
    }
}

private extension Dictionary where Key == String, Value == Any {
    func firstNonEmptyString(keys: [String]) -> String? {
        for key in keys {
            if let value = self[key] as? String {
                let clean = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty { return clean }
            }
        }
        return nil
    }

    func firstTimestamp(keys: [String]) -> Timestamp? {
        for key in keys {
            if let timestamp = self[key] as? Timestamp {
                return timestamp
            }
            if let date = self[key] as? Date {
                return Timestamp(date: date)
            }
            if let number = self[key] as? NSNumber {
                let raw = number.doubleValue
                if raw > 1_000_000_000_000 {
                    return Timestamp(date: Date(timeIntervalSince1970: raw / 1000.0))
                }
                if raw > 0 {
                    return Timestamp(date: Date(timeIntervalSince1970: raw))
                }
            }
        }
        return nil
    }
}
