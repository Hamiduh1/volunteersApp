import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class JobsRepository {
    private let db = Firestore.firestore()

    func fetchJobs(limit: Int = 50) async throws -> [JobRecord] {
        let snapshot = try await db.collection(FirestoreCollection.jobs.rawValue)
            .limit(to: limit)
            .getDocuments()
        return snapshot.documents.compactMap { try? $0.data(as: JobRecord.self) }
    }

    func hasApplied(jobId: String, uid: String) async throws -> Bool {
        let rootId = "\(uid)_\(jobId)"
        let rootSnap = try await db.collection(FirestoreCollection.applications.rawValue)
            .document(rootId)
            .getDocument()
        if rootSnap.exists { return true }

        let subSnap = try await db.collection(FirestoreCollection.jobs.rawValue)
            .document(jobId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        return subSnap.exists
    }

    func fetchJob(jobId: String) async throws -> JobRecord? {
        let snapshot = try await db.collection(FirestoreCollection.jobs.rawValue)
            .document(jobId)
            .getDocument()
        return try? snapshot.data(as: JobRecord.self)
    }

    func applyToJob(jobId: String, user: AppSessionUser, userName: String? = nil) async throws {
        let jobRef = db.collection(FirestoreCollection.jobs.rawValue).document(jobId)
        let jobDoc = try await jobRef.getDocument()
        let jobData = jobDoc.data() ?? [:]

        let employerUid = (jobData["employerUid"] as? String) ?? ""
        let employerId = (jobData["employerId"] as? String) ?? employerUid
        let jobTitle = (jobData["title"] as? String) ?? ""
        let orgName = (jobData["employerName"] as? String) ?? ""

        let rootId = "\(user.uid)_\(jobId)"
        let payload: [String: Any] = [
            "applicationId": rootId,
            "jobId": jobId,
            "jobTitle": jobTitle,
            "organizationName": orgName,
            "userId": user.uid,
            "volunteerUid": user.uid,
            "volunteerName": userName ?? "",
            "volunteerEmail": user.email ?? "",
            "employerUid": employerUid,
            "employerId": employerId,
            "status": "PENDING",
            "appliedAt": FieldValue.serverTimestamp(),
            "appliedDate": FieldValue.serverTimestamp(),
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ]

        let rootRef = db.collection(FirestoreCollection.applications.rawValue).document(rootId)
        let subRef = jobRef.collection(FirestoreSubcollection.applications.rawValue).document(user.uid)

        let batch = db.batch()
        batch.setData(payload, forDocument: rootRef, merge: true)
        batch.setData(payload, forDocument: subRef, merge: true)
        try await batch.commit()
    }

    func fetchJobApplication(jobId: String, uid: String) async throws -> JobApplicationRecord? {
        let rootId = "\(uid)_\(jobId)"
        let rootSnap = try await db.collection(FirestoreCollection.applications.rawValue)
            .document(rootId)
            .getDocument()
        if rootSnap.exists, let app = try? rootSnap.data(as: JobApplicationRecord.self) {
            return app
        }

        let subSnap = try await db.collection(FirestoreCollection.jobs.rawValue)
            .document(jobId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        guard subSnap.exists else { return nil }
        return try? subSnap.data(as: JobApplicationRecord.self)
    }
}
