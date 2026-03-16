import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

struct OrganizerManagedApplicationItem: Identifiable {
    let id: String
    let eventId: String
    let applicationId: String
    let documentId: String
    let volunteerId: String
    let volunteerName: String
    let volunteerEmail: String
    let status: ApplicationStatus
    let eventTitle: String
    let appliedAt: Date?
}

final class OrganizerRepository {
    private let db = Firestore.firestore()

    func fetchHostedEvents(uid: String, limit: Int = 100) async throws -> [EventRecord] {
        async let byOrganizerId = db.collection(FirestoreCollection.events.rawValue)
            .whereField("organizerId", isEqualTo: uid)
            .limit(to: limit)
            .getDocuments()
        async let byOrganizerUid = db.collection(FirestoreCollection.events.rawValue)
            .whereField("organizerUid", isEqualTo: uid)
            .limit(to: limit)
            .getDocuments()

        let snapshots = try await [byOrganizerId, byOrganizerUid]
        var merged: [String: EventRecord] = [:]
        for snap in snapshots {
            for doc in snap.documents {
                if let event = try? doc.data(as: EventRecord.self) {
                    merged[doc.documentID] = event
                }
            }
        }

        return merged.values.sorted {
            let l = $0.eventDateTime?.dateValue() ?? .distantFuture
            let r = $1.eventDateTime?.dateValue() ?? .distantFuture
            return l < r
        }
    }

    func fetchManagedApplications(uid: String) async throws -> [OrganizerManagedApplicationItem] {
        async let byOrganizerId = db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
            .whereField("organizerId", isEqualTo: uid)
            .getDocuments()
        async let byOrganizerUid = db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
            .whereField("organizerUid", isEqualTo: uid)
            .getDocuments()

        let snapshots = try await [byOrganizerId, byOrganizerUid]
        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        for snap in snapshots {
            for doc in snap.documents {
                // Keep only event applications under events/{eventId}/applications/{appId}.
                let segments = doc.reference.path.split(separator: "/")
                guard segments.count >= 4 else { continue }
                guard segments[0] == "events", segments[2] == "applications" else { continue }
                docsByPath[doc.reference.path] = doc
            }
        }

        let eventIds = Set(docsByPath.keys.compactMap { path in
            let segments = path.split(separator: "/")
            guard segments.count >= 2 else { return nil }
            return String(segments[1])
        })
        let eventMap = try await fetchEventTitles(ids: Array(eventIds))

        var items: [OrganizerManagedApplicationItem] = []
        for doc in docsByPath.values {
            let app = try? doc.data(as: EventApplicationRecord.self)
            let segments = doc.reference.path.split(separator: "/").map(String.init)
            let eventId = (app?.eventId?.isEmpty == false ? app?.eventId : nil) ?? (segments.count > 1 ? segments[1] : "")
            let applicationId = app?.applicationId ?? (segments.count > 3 ? segments[3] : doc.documentID)
            let documentId = segments.count > 3 ? segments[3] : doc.documentID
            let volunteerId = app?.userId ?? app?.volunteerUid ?? app?.volunteerId ?? ""

            items.append(
                OrganizerManagedApplicationItem(
                    id: "\(eventId)-\(applicationId)",
                    eventId: eventId,
                    applicationId: applicationId,
                    documentId: documentId,
                    volunteerId: volunteerId,
                    volunteerName: app?.volunteerName ?? "Volunteer",
                    volunteerEmail: app?.volunteerEmail ?? "",
                    status: app?.status ?? .unknown,
                    eventTitle: eventMap[eventId] ?? "Event",
                    appliedAt: app?.appliedAt?.dateValue() ?? app?.appliedDate?.dateValue()
                )
            )
        }

        return items.sorted {
            let l = $0.appliedAt ?? .distantPast
            let r = $1.appliedAt ?? .distantPast
            return l > r
        }
    }

    func updateApplicationStatus(eventId: String, documentId: String, status: ApplicationStatus) async throws {
        let ref = db.collection(FirestoreCollection.events.rawValue)
            .document(eventId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(documentId)

        try await ref.setData(
            [
                "status": status.rawValue,
                "lastUpdatedAt": FieldValue.serverTimestamp()
            ],
            merge: true
        )
    }

    private func fetchEventTitles(ids: [String]) async throws -> [String: String] {
        var out: [String: String] = [:]
        for id in ids where !id.isEmpty {
            let snap = try await db.collection(FirestoreCollection.events.rawValue).document(id).getDocument()
            out[id] = (snap.data()?["title"] as? String) ?? "Event"
        }
        return out
    }
}
