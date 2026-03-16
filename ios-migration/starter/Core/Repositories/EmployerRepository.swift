import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

struct EmployerManagedApplicationItem: Identifiable {
    let id: String
    let applicationId: String
    let jobId: String
    let jobTitle: String
    let volunteerId: String
    let volunteerName: String
    let volunteerEmail: String
    let status: ApplicationStatus
    let appliedAt: Date?
}

final class EmployerRepository {
    private let db = Firestore.firestore()

    func fetchPostedJobs(uid: String, limit: Int = 100) async throws -> [JobRecord] {
        async let byEmployerUid = db.collection(FirestoreCollection.jobs.rawValue)
            .whereField("employerUid", isEqualTo: uid)
            .limit(to: limit)
            .getDocuments()
        async let byEmployerId = db.collection(FirestoreCollection.jobs.rawValue)
            .whereField("employerId", isEqualTo: uid)
            .limit(to: limit)
            .getDocuments()

        let snapshots = try await [byEmployerUid, byEmployerId]
        var merged: [String: JobRecord] = [:]
        for snap in snapshots {
            for doc in snap.documents {
                if let job = try? doc.data(as: JobRecord.self) {
                    merged[doc.documentID] = job
                }
            }
        }
        return merged.values.sorted { ($0.title ?? "") < ($1.title ?? "") }
    }

    func fetchManagedApplications(uid: String) async throws -> [EmployerManagedApplicationItem] {
        async let byEmployerUid = db.collection(FirestoreCollection.applications.rawValue)
            .whereField("employerUid", isEqualTo: uid)
            .getDocuments()
        async let byEmployerId = db.collection(FirestoreCollection.applications.rawValue)
            .whereField("employerId", isEqualTo: uid)
            .getDocuments()

        let snapshots = try await [byEmployerUid, byEmployerId]
        var docsById: [String: QueryDocumentSnapshot] = [:]
        for snap in snapshots {
            for doc in snap.documents {
                docsById[doc.documentID] = doc
            }
        }

        return docsById.values.compactMap { doc in
            let app = try? doc.data(as: JobApplicationRecord.self)
            let jobId = app?.jobId ?? ""
            guard !jobId.isEmpty else { return nil }

            return EmployerManagedApplicationItem(
                id: doc.documentID,
                applicationId: doc.documentID,
                jobId: jobId,
                jobTitle: app?.jobTitle ?? "Job",
                volunteerId: app?.userId ?? app?.volunteerUid ?? "",
                volunteerName: app?.volunteerName ?? "Volunteer",
                volunteerEmail: app?.volunteerEmail ?? "",
                status: app?.status ?? .unknown,
                appliedAt: app?.appliedAt?.dateValue() ?? app?.appliedDate?.dateValue()
            )
        }
        .sorted {
            let l = $0.appliedAt ?? .distantPast
            let r = $1.appliedAt ?? .distantPast
            return l > r
        }
    }

    func updateApplicationStatus(applicationId: String, status: ApplicationStatus) async throws {
        let rootRef = db.collection(FirestoreCollection.applications.rawValue).document(applicationId)
        let rootSnap = try await rootRef.getDocument()
        guard rootSnap.exists else { return }

        let data = rootSnap.data() ?? [:]
        let jobId = data["jobId"] as? String
        let volunteerId = (data["userId"] as? String) ?? (data["volunteerUid"] as? String)

        try await rootRef.setData(
            [
                "status": status.rawValue,
                "lastUpdatedAt": FieldValue.serverTimestamp()
            ],
            merge: true
        )

        if let jobId, !jobId.isEmpty, let volunteerId, !volunteerId.isEmpty {
            let subRef = db.collection(FirestoreCollection.jobs.rawValue)
                .document(jobId)
                .collection(FirestoreSubcollection.applications.rawValue)
                .document(volunteerId)
            try? await subRef.setData(
                [
                    "status": status.rawValue,
                    "lastUpdatedAt": FieldValue.serverTimestamp()
                ],
                merge: true
            )
        }
    }
}
