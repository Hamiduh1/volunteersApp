import Foundation
import FirebaseFirestore

struct EmployerManagedApplicationItem: Identifiable {
    let id: String
    let applicationId: String
    let documentId: String
    let rootApplicationId: String?
    let sourcePath: String
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
        let queries: [Query] = [
            db.collection(FirestoreCollection.jobs.rawValue)
                .whereField("employerUid", isEqualTo: uid)
                .limit(to: limit),
            db.collection(FirestoreCollection.jobs.rawValue)
                .whereField("employerId", isEqualTo: uid)
                .limit(to: limit)
        ]

        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        var lastError: Error?
        for query in queries {
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents {
                    docsByPath[doc.reference.path] = doc
                }
            } catch {
                lastError = error
            }
        }

        if docsByPath.isEmpty {
            do {
                let fallback = try await db.collection(FirestoreCollection.jobs.rawValue)
                    .limit(to: max(limit * 3, 150))
                    .getDocuments()
                for doc in fallback.documents {
                    let data = doc.data()
                    let employer = data.firstNonEmptyString(keys: ["employerUid", "employerId"]) ?? ""
                    if employer == uid {
                        docsByPath[doc.reference.path] = doc
                    }
                }
            } catch {
                if lastError == nil { lastError = error }
            }
        }

        if docsByPath.isEmpty, let lastError {
            throw lastError
        }

        return docsByPath.values
            .compactMap(parseJob)
            .sorted { ($0.title ?? "") < ($1.title ?? "") }
    }

    func fetchManagedApplications(uid: String) async throws -> [EmployerManagedApplicationItem] {
        let queries: [Query] = [
            db.collection(FirestoreCollection.applications.rawValue)
                .whereField("employerUid", isEqualTo: uid),
            db.collection(FirestoreCollection.applications.rawValue)
                .whereField("employerId", isEqualTo: uid),
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("employerUid", isEqualTo: uid),
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("employerId", isEqualTo: uid)
        ]

        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        var lastError: Error?
        for query in queries {
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents {
                    let data = doc.data()
                    if !isJobApplication(path: doc.reference.path, data: data) { continue }
                    docsByPath[doc.reference.path] = doc
                }
            } catch {
                lastError = error
            }
        }

        // Fallback: inspect sub-applications under posted jobs.
        if docsByPath.isEmpty {
            do {
                let jobs = try await fetchPostedJobs(uid: uid, limit: 120)
                for job in jobs {
                    let jobId = job.id ?? ""
                    guard !jobId.isEmpty else { continue }
                    do {
                        let snapshot = try await db.collection(FirestoreCollection.jobs.rawValue)
                            .document(jobId)
                            .collection(FirestoreSubcollection.applications.rawValue)
                            .limit(to: 250)
                            .getDocuments()
                        for doc in snapshot.documents {
                            docsByPath[doc.reference.path] = doc
                        }
                    } catch {
                        continue
                    }
                }
            } catch {
                if lastError == nil { lastError = error }
            }
        }

        if docsByPath.isEmpty, let lastError {
            throw lastError
        }

        let jobIds = Set(docsByPath.values.compactMap { doc in
            let data = doc.data()
            let raw = data.firstNonEmptyString(keys: ["jobId"]) ?? jobIdFromPath(doc.reference.path)
            let normalized = normalizeDocId(raw, collection: FirestoreCollection.jobs.rawValue)
            return normalized.isEmpty ? nil : normalized
        })
        let jobTitleMap = try await fetchJobTitles(ids: Array(jobIds))

        let items = docsByPath.values.compactMap { doc in
            parseManagedApplication(doc: doc, jobTitleMap: jobTitleMap)
        }

        return items.sorted {
            let l = $0.appliedAt ?? .distantPast
            let r = $1.appliedAt ?? .distantPast
            return l > r
        }
    }

    func updateApplicationStatus(item: EmployerManagedApplicationItem, status: ApplicationStatus) async throws {
        let patch: [String: Any] = [
            "status": status.rawValue,
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ]

        var didWrite = false
        let candidateRootIds = Set(
            ([item.applicationId, item.documentId] + [
                item.rootApplicationId
            ].compactMap { $0 }).filter { !$0.isEmpty }
        )

        for rootId in candidateRootIds {
            let rootRef = db.collection(FirestoreCollection.applications.rawValue).document(rootId)
            if let snap = try? await rootRef.getDocument(), snap.exists {
                try? await rootRef.setData(patch, merge: true)
                didWrite = true
            }
        }

        let subCollection = db.collection(FirestoreCollection.jobs.rawValue)
            .document(item.jobId)
            .collection(FirestoreSubcollection.applications.rawValue)
        let candidateSubIds = Set(
            [item.documentId, item.applicationId, item.volunteerId].filter { !$0.isEmpty }
        )
        for subId in candidateSubIds {
            let subRef = subCollection.document(subId)
            if let snap = try? await subRef.getDocument(), snap.exists {
                try? await subRef.setData(patch, merge: true)
                didWrite = true
            }
        }

        // Extra reconciliation over root docs by jobId.
        do {
            let rootByJob = try await db.collection(FirestoreCollection.applications.rawValue)
                .whereField("jobId", isEqualTo: item.jobId)
                .limit(to: 150)
                .getDocuments()
            for doc in rootByJob.documents {
                let data = doc.data()
                let volunteer = data.firstNonEmptyString(keys: ["userId", "volunteerUid", "volunteerId"])
                let appId = data.firstNonEmptyString(keys: ["applicationId", "id"]) ?? doc.documentID
                let matchesVolunteer = volunteer == item.volunteerId
                let matchesAppId = appId == item.applicationId || doc.documentID == item.applicationId || doc.documentID == item.documentId
                guard matchesVolunteer || matchesAppId else { continue }
                try? await doc.reference.setData(patch, merge: true)
                didWrite = true
            }
        } catch {
            // Ignore best-effort reconciliation failures.
        }

        if !didWrite {
            throw NSError(
                domain: "EmployerRepository",
                code: 404,
                userInfo: [NSLocalizedDescriptionKey: "Application record could not be located for update."]
            )
        }
    }

    func updateApplicationStatus(applicationId: String, status: ApplicationStatus) async throws {
        let rootRef = db.collection(FirestoreCollection.applications.rawValue).document(applicationId)
        let rootSnap = try await rootRef.getDocument()
        guard rootSnap.exists else {
            throw NSError(
                domain: "EmployerRepository",
                code: 404,
                userInfo: [NSLocalizedDescriptionKey: "Application record not found."]
            )
        }

        let data = rootSnap.data() ?? [:]
        let jobId = normalizeDocId(data.firstNonEmptyString(keys: ["jobId"]), collection: FirestoreCollection.jobs.rawValue)
        let volunteerId = data.firstNonEmptyString(keys: ["userId", "volunteerUid", "volunteerId"]) ?? ""
        let item = EmployerManagedApplicationItem(
            id: applicationId,
            applicationId: data.firstNonEmptyString(keys: ["applicationId", "id"]) ?? applicationId,
            documentId: applicationId,
            rootApplicationId: applicationId,
            sourcePath: rootRef.path,
            jobId: jobId,
            jobTitle: data.firstNonEmptyString(keys: ["jobTitle", "title"]) ?? "Job",
            volunteerId: volunteerId,
            volunteerName: data.firstNonEmptyString(keys: ["volunteerName", "name"]) ?? "Volunteer",
            volunteerEmail: data.firstNonEmptyString(keys: ["volunteerEmail", "email"]) ?? "",
            status: parseStatus(data["status"]),
            appliedAt: data.firstDate(keys: ["appliedAt", "appliedDate", "createdAt", "timestamp"])
        )
        try await updateApplicationStatus(item: item, status: status)
    }

    func createJobPosting(
        uid: String,
        title: String,
        description: String,
        locationString: String,
        category: String,
        jobType: String,
        salaryOrCompensation: String,
        applicationDeadline: Date
    ) async throws -> String {
        let employerName = try await fetchUserDisplayName(uid: uid)
        let ref = db.collection(FirestoreCollection.jobs.rawValue).document()

        try await ref.setData([
            "title": title.trimmingCharacters(in: .whitespacesAndNewlines),
            "description": description.trimmingCharacters(in: .whitespacesAndNewlines),
            "locationString": locationString.trimmingCharacters(in: .whitespacesAndNewlines),
            "category": category.trimmingCharacters(in: .whitespacesAndNewlines),
            "jobType": jobType.trimmingCharacters(in: .whitespacesAndNewlines),
            "salaryOrCompensation": salaryOrCompensation.trimmingCharacters(in: .whitespacesAndNewlines),
            "applicationDeadline": Timestamp(date: applicationDeadline),
            "postedDate": FieldValue.serverTimestamp(),
            "status": "OPEN",
            "applicantsCount": 0,
            "employerUid": uid,
            "employerId": uid,
            "employerName": employerName,
            "createdAt": FieldValue.serverTimestamp(),
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ], merge: true)

        return ref.documentID
    }

    func updateJobPosting(
        jobId: String,
        uid: String,
        title: String,
        description: String,
        locationString: String,
        category: String,
        jobType: String,
        salaryOrCompensation: String,
        applicationDeadline: Date
    ) async throws {
        let employerName = try await fetchUserDisplayName(uid: uid)
        let ref = db.collection(FirestoreCollection.jobs.rawValue).document(jobId)

        try await ref.setData([
            "title": title.trimmingCharacters(in: .whitespacesAndNewlines),
            "description": description.trimmingCharacters(in: .whitespacesAndNewlines),
            "locationString": locationString.trimmingCharacters(in: .whitespacesAndNewlines),
            "category": category.trimmingCharacters(in: .whitespacesAndNewlines),
            "jobType": jobType.trimmingCharacters(in: .whitespacesAndNewlines),
            "salaryOrCompensation": salaryOrCompensation.trimmingCharacters(in: .whitespacesAndNewlines),
            "applicationDeadline": Timestamp(date: applicationDeadline),
            "employerUid": uid,
            "employerId": uid,
            "employerName": employerName,
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ], merge: true)
    }

    func deleteJobPosting(jobId: String) async throws {
        let ref = db.collection(FirestoreCollection.jobs.rawValue).document(jobId)
        do {
            try await ref.delete()
        } catch {
            try await ref.setData([
                "isDeleted": true,
                "status": "CLOSED",
                "lastUpdatedAt": FieldValue.serverTimestamp()
            ], merge: true)
        }
    }

    private func fetchUserDisplayName(uid: String) async throws -> String {
        let userSnap = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let data = userSnap.data() ?? [:]
        return data.firstNonEmptyString(keys: ["name", "username", "email"]) ?? "Employer"
    }

    private func fetchJobTitles(ids: [String]) async throws -> [String: String] {
        var map: [String: String] = [:]
        for id in ids where !id.isEmpty {
            do {
                let snap = try await db.collection(FirestoreCollection.jobs.rawValue).document(id).getDocument()
                let title = (snap.data() ?? [:]).firstNonEmptyString(keys: ["title", "name"]) ?? "Job"
                map[id] = title
            } catch {
                continue
            }
        }
        return map
    }

    private func parseManagedApplication(
        doc: QueryDocumentSnapshot,
        jobTitleMap: [String: String]
    ) -> EmployerManagedApplicationItem? {
        let data = doc.data()
        let jobId = normalizeDocId(
            data.firstNonEmptyString(keys: ["jobId"]) ?? jobIdFromPath(doc.reference.path),
            collection: FirestoreCollection.jobs.rawValue
        )
        guard !jobId.isEmpty else { return nil }

        let documentId = applicationDocumentIdFromPath(doc.reference.path) ?? doc.documentID
        let applicationId = data.firstNonEmptyString(keys: ["applicationId", "id"]) ?? documentId
        let rootApplicationId = data.firstNonEmptyString(keys: ["rootApplicationId"])
        let volunteerId = data.firstNonEmptyString(keys: ["userId", "volunteerUid", "volunteerId", "applicantId"]) ?? ""
        let volunteerName = data.firstNonEmptyString(keys: ["volunteerName", "name", "applicantName", "userName"]) ?? "Volunteer"
        let volunteerEmail = data.firstNonEmptyString(keys: ["volunteerEmail", "email", "userEmail"]) ?? ""
        let status = parseStatus(data["status"])
        let appliedAt = data.firstDate(keys: ["appliedAt", "appliedDate", "createdAt", "timestamp", "lastUpdatedAt"])
        let title = data.firstNonEmptyString(keys: ["jobTitle", "title"]) ?? jobTitleMap[jobId] ?? "Job"

        return EmployerManagedApplicationItem(
            id: "\(jobId)-\(documentId)-\(applicationId)",
            applicationId: applicationId,
            documentId: documentId,
            rootApplicationId: rootApplicationId,
            sourcePath: doc.reference.path,
            jobId: jobId,
            jobTitle: title,
            volunteerId: volunteerId,
            volunteerName: volunteerName,
            volunteerEmail: volunteerEmail,
            status: status,
            appliedAt: appliedAt
        )
    }

    private func parseJob(_ doc: QueryDocumentSnapshot) -> JobRecord? {
        let data = doc.data()
        if data.truthy(keys: ["isDeleted"]) { return nil }

        return JobRecord(
            id: doc.documentID,
            title: data.firstNonEmptyString(keys: ["title", "name"]),
            employerUid: data.firstNonEmptyString(keys: ["employerUid"]),
            employerId: data.firstNonEmptyString(keys: ["employerId"]),
            employerName: data.firstNonEmptyString(keys: ["employerName", "companyName"]),
            description: data.firstNonEmptyString(keys: ["description", "details"]),
            responsibilities: data.firstStringArray(keys: ["responsibilities"]),
            locationString: data.firstNonEmptyString(keys: ["locationString", "location"]),
            locationIsRemote: data.firstBool(keys: ["locationIsRemote", "isRemote"]),
            category: data.firstNonEmptyString(keys: ["category"]),
            jobType: data.firstNonEmptyString(keys: ["jobType", "type"]),
            postedDate: data.firstTimestamp(keys: ["postedDate", "createdAt"]),
            applicationDeadline: data.firstTimestamp(keys: ["applicationDeadline", "deadline"]),
            status: data.firstNonEmptyString(keys: ["status"]),
            requiredSkills: data.firstStringArray(keys: ["requiredSkills", "skills"]),
            preferredSkills: data.firstStringArray(keys: ["preferredSkills"]),
            salaryOrCompensation: data.firstNonEmptyString(keys: ["salaryOrCompensation", "compensation", "salary"]),
            totalSlots: data.firstInt(keys: ["totalSlots", "slots"]),
            slotsFilled: data.firstInt(keys: ["slotsFilled"]),
            applicantsCount: data.firstInt(keys: ["applicantsCount", "applicationsCount"])
        )
    }

    private func parseStatus(_ raw: Any?) -> ApplicationStatus {
        let normalized = (raw as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "-", with: "_")
        return ApplicationStatus(rawValue: normalized ?? "") ?? .unknown
    }

    private func isJobApplication(path: String, data: [String: Any]) -> Bool {
        if data.firstNonEmptyString(keys: ["jobId"]) != nil { return true }
        let segments = path.split(separator: "/").map(String.init)
        return segments.count >= 4
            && segments[0] == FirestoreCollection.jobs.rawValue
            && segments[2] == FirestoreSubcollection.applications.rawValue
    }

    private func jobIdFromPath(_ path: String) -> String? {
        let segments = path.split(separator: "/").map(String.init)
        guard segments.count >= 2, segments[0] == FirestoreCollection.jobs.rawValue else { return nil }
        return segments[1]
    }

    private func applicationDocumentIdFromPath(_ path: String) -> String? {
        let segments = path.split(separator: "/").map(String.init)
        guard segments.count >= 4, segments[2] == FirestoreSubcollection.applications.rawValue else { return nil }
        return segments[3]
    }

    private func normalizeDocId(_ raw: String?, collection: String) -> String {
        let trimmed = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let segments = trimmed.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        guard !segments.isEmpty else { return "" }
        if let idx = segments.firstIndex(of: collection), segments.count > idx + 1 {
            return segments[idx + 1]
        }
        return segments.last ?? trimmed
    }
}

private extension Dictionary where Key == String, Value == Any {
    func firstNonEmptyString(keys: [String]) -> String? {
        for key in keys {
            if let value = self[key] as? String {
                let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cleaned.isEmpty { return cleaned }
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
            if let text = self[key] as? String, let raw = Double(text.trimmingCharacters(in: .whitespacesAndNewlines)) {
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

    func firstDate(keys: [String]) -> Date? {
        firstTimestamp(keys: keys)?.dateValue()
    }

    func firstInt(keys: [String]) -> Int? {
        for key in keys {
            if let number = self[key] as? NSNumber {
                return number.intValue
            }
            if let text = self[key] as? String, let value = Int(text.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return value
            }
        }
        return nil
    }

    func firstBool(keys: [String]) -> Bool? {
        for key in keys {
            if let value = self[key] as? Bool {
                return value
            }
            if let number = self[key] as? NSNumber {
                return number.intValue != 0
            }
            if let text = self[key] as? String {
                let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                if ["true", "yes", "1"].contains(normalized) { return true }
                if ["false", "no", "0"].contains(normalized) { return false }
            }
        }
        return nil
    }

    func firstStringArray(keys: [String]) -> [String]? {
        for key in keys {
            if let values = self[key] as? [String] {
                let cleaned = values.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
                if !cleaned.isEmpty { return cleaned }
            }
            if let values = self[key] as? [Any] {
                let cleaned = values.compactMap { $0 as? String }
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
                if !cleaned.isEmpty { return cleaned }
            }
            if let value = self[key] as? String {
                let parts = value.split(separator: ",")
                    .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
                if !parts.isEmpty { return parts }
            }
        }
        return nil
    }

    func truthy(keys: [String]) -> Bool {
        for key in keys {
            if let value = self[key] as? Bool { return value }
            if let number = self[key] as? NSNumber { return number.intValue != 0 }
            if let text = self[key] as? String {
                let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                if ["true", "yes", "1"].contains(normalized) { return true }
            }
        }
        return false
    }
}
