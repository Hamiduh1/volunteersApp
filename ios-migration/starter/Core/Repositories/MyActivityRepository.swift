import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class MyActivityRepository {
    private let db = Firestore.firestore()

    func fetchActivity(for uid: String) async throws -> [VolunteerActivityItem] {
        async let eventApps = fetchEventApplications(uid: uid)
        async let jobApps = fetchJobApplications(uid: uid)
        var merged = try await eventApps + jobApps

        merged.sort { lhs, rhs in
            let l = lhs.appliedAt ?? .distantPast
            let r = rhs.appliedAt ?? .distantPast
            return l > r
        }
        return merged
    }

    private func fetchEventApplications(uid: String) async throws -> [VolunteerActivityItem] {
        let queries: [Query] = [
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("userId", isEqualTo: uid),
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("volunteerUid", isEqualTo: uid),
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("volunteerId", isEqualTo: uid)
        ]

        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        for query in queries {
            let snapshot = try await query.getDocuments()
            for doc in snapshot.documents {
                // Keep only event applications from events/{eventId}/applications/{appId}.
                let segments = doc.reference.path.split(separator: "/")
                guard segments.count >= 4 else { continue }
                if segments[0] != "events" || segments[2] != "applications" { continue }
                docsByPath[doc.reference.path] = doc
            }
        }

        let eventIds = Set(docsByPath.values.compactMap { extractEventId(from: $0.reference.path) })
        let eventsById = try await fetchEventsById(Array(eventIds))

        return docsByPath.values.compactMap { doc in
            let app = try? doc.data(as: EventApplicationRecord.self)
            let eventId = (app?.eventId?.isEmpty == false ? app?.eventId : nil)
                ?? extractEventId(from: doc.reference.path)
                ?? ""
            guard !eventId.isEmpty else { return nil }

            let eventTitle = eventsById[eventId]?.title ?? "Event"
            let status = app?.status ?? .unknown
            let appliedDate = app?.appliedAt?.dateValue() ?? app?.appliedDate?.dateValue()

            return VolunteerActivityItem(
                id: "event-\(doc.documentID)-\(eventId)",
                type: .event,
                referenceId: eventId,
                title: eventTitle,
                subtitle: "Event application",
                status: status,
                appliedAt: appliedDate
            )
        }
    }

    private func fetchJobApplications(uid: String) async throws -> [VolunteerActivityItem] {
        let queries: [Query] = [
            db.collection(FirestoreCollection.applications.rawValue)
                .whereField("userId", isEqualTo: uid),
            db.collection(FirestoreCollection.applications.rawValue)
                .whereField("volunteerUid", isEqualTo: uid)
        ]

        var docsById: [String: QueryDocumentSnapshot] = [:]
        for query in queries {
            let snapshot = try await query.getDocuments()
            for doc in snapshot.documents {
                docsById[doc.documentID] = doc
            }
        }

        var items: [VolunteerActivityItem] = []
        for doc in docsById.values {
            guard let app = try? doc.data(as: JobApplicationRecord.self) else { continue }
            let jobId = app.jobId ?? ""
            guard !jobId.isEmpty else { continue }

            let title = app.jobTitle ?? "Job"
            let status = app.status ?? .unknown
            let appliedDate = app.appliedAt?.dateValue() ?? app.appliedDate?.dateValue()

            items.append(
                VolunteerActivityItem(
                    id: "job-\(doc.documentID)-\(jobId)",
                    type: .job,
                    referenceId: jobId,
                    title: title,
                    subtitle: app.employerUid ?? app.employerId ?? "Job application",
                    status: status,
                    appliedAt: appliedDate
                )
            )
        }
        return items
    }

    private func fetchEventsById(_ eventIds: [String]) async throws -> [String: EventRecord] {
        var result: [String: EventRecord] = [:]
        for id in eventIds where !id.isEmpty {
            let doc = try await db.collection(FirestoreCollection.events.rawValue).document(id).getDocument()
            if let event = try? doc.data(as: EventRecord.self) {
                result[id] = event
            }
        }
        return result
    }

    private func extractEventId(from path: String) -> String? {
        let segments = path.split(separator: "/").map(String.init)
        guard segments.count >= 2 else { return nil }
        guard segments[0] == FirestoreCollection.events.rawValue else { return nil }
        return segments[1]
    }
}
