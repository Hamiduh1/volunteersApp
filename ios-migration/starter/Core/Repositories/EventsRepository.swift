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
        return snapshot.documents.compactMap { try? $0.data(as: EventRecord.self) }
    }

    func hasApplied(eventId: String, uid: String) async throws -> Bool {
        let snap = try await db.collection(FirestoreCollection.events.rawValue)
            .document(eventId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        return snap.exists
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
}
