import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class JobsRepository {
    private let db = Firestore.firestore()

    func fetchJobs(limit: Int = 50) async throws -> [JobRecord] {
        let snapshot = try await db.collection(FirestoreCollection.jobs.rawValue)
            .limit(to: limit)
            .getDocuments()
        return snapshot.documents.compactMap { doc in
            if (doc.data()["isDeleted"] as? Bool) == true { return nil }
            if let status = (doc.data()["status"] as? String)?.uppercased(), status == "CLOSED" {
                return nil
            }
            return try? doc.data(as: JobRecord.self)
        }
    }

    func hasApplied(jobId: String, uid: String) async throws -> Bool {
        let normalizedJobId = normalizeDocumentId(jobId)
        guard !normalizedJobId.isEmpty else { return false }

        let rootIds = [
            "\(normalizedJobId)_\(uid)", // Android canonical
            "\(uid)_\(normalizedJobId)"  // legacy iOS fallback
        ]
        for rootId in rootIds {
            let rootSnap = try await db.collection(FirestoreCollection.applications.rawValue)
                .document(rootId)
                .getDocument()
            if rootSnap.exists { return true }
        }

        let subSnap = try await db.collection(FirestoreCollection.jobs.rawValue)
            .document(normalizedJobId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        return subSnap.exists
    }

    func fetchJob(jobId: String) async throws -> JobRecord? {
        let normalizedJobId = normalizeDocumentId(jobId)
        guard !normalizedJobId.isEmpty else { return nil }
        let snapshot = try await db.collection(FirestoreCollection.jobs.rawValue)
            .document(normalizedJobId)
            .getDocument()
        return try? snapshot.data(as: JobRecord.self)
    }

    func applyToJob(jobId: String, user: AppSessionUser, userName: String? = nil) async throws {
        let normalizedJobId = normalizeDocumentId(jobId)
        guard !normalizedJobId.isEmpty else { return }
        if try await hasApplied(jobId: normalizedJobId, uid: user.uid) {
            throw NSError(
                domain: "JobsRepository",
                code: 409,
                userInfo: [NSLocalizedDescriptionKey: "You have already applied for this job."]
            )
        }

        let jobRef = db.collection(FirestoreCollection.jobs.rawValue).document(normalizedJobId)
        let jobDoc = try await jobRef.getDocument()
        let jobData = jobDoc.data() ?? [:]

        let employerUid = (jobData["employerUid"] as? String) ?? ""
        let employerId = (jobData["employerId"] as? String) ?? employerUid
        let jobTitle = (jobData["title"] as? String) ?? ""
        let roleTitle = (jobData["jobTitle"] as? String) ?? ""
        let orgName = (jobData["organizationName"] as? String) ?? (jobData["employerName"] as? String) ?? ""

        let rootId = "\(normalizedJobId)_\(user.uid)"
        let subCollectionId = user.uid
        let candidateVolunteerName = userName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let volunteerName = candidateVolunteerName.isEmpty
            ? (try await fetchVolunteerDisplayName(uid: user.uid))
            : candidateVolunteerName

        let payload: [String: Any] = [
            "applicationId": rootId,
            "jobId": normalizedJobId,
            "jobTitle": jobTitle,
            "roleTitle": roleTitle,
            "organizationName": orgName,
            "userId": user.uid,
            "volunteerUid": user.uid,
            "volunteerName": volunteerName,
            "volunteerEmail": user.email ?? "",
            "employerUid": employerUid,
            "employerId": employerId,
            "status": "pending",
            "appliedAt": FieldValue.serverTimestamp(),
            "appliedDate": FieldValue.serverTimestamp(),
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ]

        let rootRef = db.collection(FirestoreCollection.applications.rawValue).document(rootId)
        let subRef = jobRef.collection(FirestoreSubcollection.applications.rawValue).document(user.uid)
        var subPayload = payload
        subPayload["applicationId"] = subCollectionId

        let batch = db.batch()
        batch.setData(payload, forDocument: rootRef, merge: true)
        batch.setData(subPayload, forDocument: subRef, merge: true)
        try await batch.commit()
    }

    func fetchJobApplication(jobId: String, uid: String) async throws -> JobApplicationRecord? {
        let normalizedJobId = normalizeDocumentId(jobId)
        guard !normalizedJobId.isEmpty else { return nil }

        let rootIds = [
            "\(normalizedJobId)_\(uid)", // Android canonical
            "\(uid)_\(normalizedJobId)"  // legacy iOS fallback
        ]
        for rootId in rootIds {
            let rootSnap = try await db.collection(FirestoreCollection.applications.rawValue)
                .document(rootId)
                .getDocument()
            if rootSnap.exists, let app = try? rootSnap.data(as: JobApplicationRecord.self) {
                return app
            }
        }

        let subSnap = try await db.collection(FirestoreCollection.jobs.rawValue)
            .document(normalizedJobId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(uid)
            .getDocument()
        guard subSnap.exists else { return nil }
        if let decoded = try? subSnap.data(as: JobApplicationRecord.self) {
            return decoded
        }

        let data = subSnap.data() ?? [:]
        return JobApplicationRecord(
            id: subSnap.documentID,
            applicationId: data.firstNonEmptyString(keys: ["applicationId", "id"]) ?? subSnap.documentID,
            jobId: data.firstNonEmptyString(keys: ["jobId"]) ?? normalizedJobId,
            jobTitle: data.firstNonEmptyString(keys: ["jobTitle", "title"]),
            userId: data.firstNonEmptyString(keys: ["userId", "volunteerUid", "volunteerId"]),
            volunteerUid: data.firstNonEmptyString(keys: ["volunteerUid", "userId", "volunteerId"]),
            volunteerName: data.firstNonEmptyString(keys: ["volunteerName", "name"]),
            volunteerEmail: data.firstNonEmptyString(keys: ["volunteerEmail", "email"]),
            employerUid: data.firstNonEmptyString(keys: ["employerUid", "employerId"]),
            employerId: data.firstNonEmptyString(keys: ["employerId", "employerUid"]),
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
        if let index = segments.firstIndex(of: FirestoreCollection.jobs.rawValue), segments.count > index + 1 {
            return segments[index + 1]
        }
        return segments.last ?? trimmed
    }

    private func fetchVolunteerDisplayName(uid: String) async throws -> String {
        let userDoc = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let data = userDoc.data() ?? [:]
        return data.firstNonEmptyString(keys: ["name", "username", "email"]) ?? "Volunteer"
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
