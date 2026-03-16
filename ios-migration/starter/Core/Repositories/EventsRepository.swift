import Foundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseFirestoreSwift

final class EventsRepository {
    private let db = Firestore.firestore()

    func fetchEvents(limit: Int = 50) async throws -> [EventRecord] {
        let snapshot = try await db.collection(FirestoreCollection.events.rawValue)
            .limit(to: limit)
            .getDocuments()
        return snapshot.documents.compactMap { doc in
            if (doc.data()["isDeleted"] as? Bool) == true { return nil }
            if let status = (doc.data()["status"] as? String)?.uppercased(), status == "CANCELLED" {
                return nil
            }
            return try? doc.data(as: EventRecord.self)
        }
    }

    func hasApplied(eventId: String, uid: String) async throws -> Bool {
        let snap = try await db.collection(FirestoreCollection.events.rawValue)
            .document(eventId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        return snap.exists
    }

    func fetchEvent(eventId: String) async throws -> EventRecord? {
        let snapshot = try await db.collection(FirestoreCollection.events.rawValue)
            .document(eventId)
            .getDocument()
        return try? snapshot.data(as: EventRecord.self)
    }

    func applyToEvent(eventId: String, user: AppSessionUser, userName: String? = nil) async throws {
        let eventRef = db.collection(FirestoreCollection.events.rawValue).document(eventId)
        let eventDoc = try await eventRef.getDocument()
        let eventData = eventDoc.data() ?? [:]

        let payload: [String: Any] = [
            "applicationId": user.uid,
            "eventId": eventId,
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
    }

    func fetchEventApplication(eventId: String, uid: String) async throws -> EventApplicationRecord? {
        let snap = try await db.collection(FirestoreCollection.events.rawValue)
            .document(eventId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        guard snap.exists else { return nil }
        return try? snap.data(as: EventApplicationRecord.self)
    }
}
