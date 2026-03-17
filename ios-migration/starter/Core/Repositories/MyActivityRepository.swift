import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class MyActivityRepository {
    private let db = Firestore.firestore()

    func fetchActivity(for uid: String) async throws -> [VolunteerActivityItem] {
        var merged: [VolunteerActivityItem] = []
        var lastError: Error?

        do {
            merged.append(contentsOf: try await fetchEventApplications(uid: uid))
        } catch {
            lastError = error
        }

        do {
            merged.append(contentsOf: try await fetchJobApplications(uid: uid))
        } catch {
            lastError = error
        }

        if merged.isEmpty, let lastError {
            throw lastError
        }

        var deduped: [String: VolunteerActivityItem] = [:]
        for item in merged {
            deduped[item.id] = item
        }

        let sorted = deduped.values.sorted { lhs, rhs in
            let l = lhs.appliedAt ?? .distantPast
            let r = rhs.appliedAt ?? .distantPast
            return l > r
        }
        return sorted
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
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents {
                    guard isEventApplicationPath(doc.reference.path) else { continue }
                    docsByPath[doc.reference.path] = doc
                }
            } catch {
                continue
            }
        }

        let eventIds = Set(docsByPath.values.compactMap { doc in
            let data = doc.data()
            let rawEventId = data.firstNonEmptyString(keys: ["eventId"]) ?? extractEventId(from: doc.reference.path)
            return normalizeDocumentId(rawEventId, collection: FirestoreCollection.events.rawValue)
        })
        let eventsById = try await fetchEventsById(Array(eventIds))

        return docsByPath.values.compactMap { doc in
            let data = doc.data()
            let rawEventId = data.firstNonEmptyString(keys: ["eventId"]) ?? extractEventId(from: doc.reference.path)
            let eventId = normalizeDocumentId(rawEventId, collection: FirestoreCollection.events.rawValue)
            guard !eventId.isEmpty else { return nil }

            let eventTitle = eventsById[eventId]?.title
                ?? data.firstNonEmptyString(keys: ["eventTitle", "title"])
                ?? "Event"
            let status = parseStatus(data: data)
            let appliedDate = data.firstDate(keys: ["appliedAt", "appliedDate", "createdAt", "timestamp"])

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
        let rootQueries: [Query] = [
            db.collection(FirestoreCollection.applications.rawValue)
                .whereField("userId", isEqualTo: uid),
            db.collection(FirestoreCollection.applications.rawValue)
                .whereField("volunteerUid", isEqualTo: uid),
            db.collection(FirestoreCollection.applications.rawValue)
                .whereField("volunteerId", isEqualTo: uid)
        ]

        let groupQueries: [Query] = [
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("userId", isEqualTo: uid),
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("volunteerUid", isEqualTo: uid),
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("volunteerId", isEqualTo: uid)
        ]

        var docsByKey: [String: QueryDocumentSnapshot] = [:]
        for query in rootQueries + groupQueries {
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents {
                    let data = doc.data()
                    guard isJobApplicationDocument(path: doc.reference.path, data: data) else { continue }
                    docsByKey["\(doc.reference.path)#\(doc.documentID)"] = doc
                }
            } catch {
                continue
            }
        }

        let jobIds = Set(docsByKey.values.compactMap { doc in
            let data = doc.data()
            let rawJobId = data.firstNonEmptyString(keys: ["jobId"]) ?? extractJobId(from: doc.reference.path)
            return normalizeDocumentId(rawJobId, collection: FirestoreCollection.jobs.rawValue)
        })
        let jobsById = try await fetchJobsById(Array(jobIds))

        let parsed: [VolunteerActivityItem] = docsByKey.values.compactMap { doc in
            let data = doc.data()
            let rawJobId = data.firstNonEmptyString(keys: ["jobId"]) ?? extractJobId(from: doc.reference.path)
            let jobId = normalizeDocumentId(rawJobId, collection: FirestoreCollection.jobs.rawValue)
            guard !jobId.isEmpty else { return nil }

            let title = data.firstNonEmptyString(keys: ["jobTitle", "title"])
                ?? jobsById[jobId]?.title
                ?? "Job"
            let status = parseStatus(data: data)
            let appliedDate = data.firstDate(keys: ["appliedAt", "appliedDate", "createdAt", "timestamp"])
            let subtitle = data.firstNonEmptyString(keys: ["organizationName", "employerName", "employerUid", "employerId"])
                ?? "Job application"

            return VolunteerActivityItem(
                id: "job-\(jobId)",
                type: .job,
                referenceId: jobId,
                title: title,
                subtitle: subtitle,
                status: status,
                appliedAt: appliedDate
            )
        }

        var bestByJobId: [String: VolunteerActivityItem] = [:]
        for item in parsed {
            if let existing = bestByJobId[item.referenceId] {
                let existingDate = existing.appliedAt ?? .distantPast
                let candidateDate = item.appliedAt ?? .distantPast
                if candidateDate > existingDate {
                    bestByJobId[item.referenceId] = item
                }
            } else {
                bestByJobId[item.referenceId] = item
            }
        }
        return Array(bestByJobId.values)
    }

    private func fetchEventsById(_ eventIds: [String]) async throws -> [String: EventRecord] {
        var result: [String: EventRecord] = [:]
        for id in eventIds where !id.isEmpty {
            do {
                let doc = try await db.collection(FirestoreCollection.events.rawValue).document(id).getDocument()
                if let event = try? doc.data(as: EventRecord.self) {
                    result[id] = event
                }
            } catch {
                continue
            }
        }
        return result
    }

    private func fetchJobsById(_ jobIds: [String]) async throws -> [String: JobRecord] {
        var result: [String: JobRecord] = [:]
        for id in jobIds where !id.isEmpty {
            do {
                let doc = try await db.collection(FirestoreCollection.jobs.rawValue).document(id).getDocument()
                if let job = try? doc.data(as: JobRecord.self) {
                    result[id] = job
                }
            } catch {
                continue
            }
        }
        return result
    }

    private func isEventApplicationPath(_ path: String) -> Bool {
        let segments = path.split(separator: "/").map(String.init)
        return segments.count >= 4
            && segments[0] == FirestoreCollection.events.rawValue
            && segments[2] == FirestoreSubcollection.applications.rawValue
    }

    private func isJobApplicationDocument(path: String, data: [String: Any]) -> Bool {
        if data.firstNonEmptyString(keys: ["jobId"]) != nil { return true }
        let segments = path.split(separator: "/").map(String.init)
        return segments.count >= 4
            && segments[0] == FirestoreCollection.jobs.rawValue
            && segments[2] == FirestoreSubcollection.applications.rawValue
    }

    private func extractEventId(from path: String) -> String? {
        let segments = path.split(separator: "/").map(String.init)
        guard segments.count >= 2 else { return nil }
        guard segments[0] == FirestoreCollection.events.rawValue else { return nil }
        return segments[1]
    }

    private func extractJobId(from path: String) -> String? {
        let segments = path.split(separator: "/").map(String.init)
        guard segments.count >= 2 else { return nil }
        guard segments[0] == FirestoreCollection.jobs.rawValue else { return nil }
        return segments[1]
    }

    private func normalizeDocumentId(_ raw: String?, collection: String) -> String {
        let trimmed = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let segments = trimmed.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        guard !segments.isEmpty else { return "" }
        if let index = segments.firstIndex(of: collection), segments.count > index + 1 {
            return segments[index + 1]
        }
        return segments.last ?? trimmed
    }

    private func parseStatus(data: [String: Any]) -> ApplicationStatus {
        let rawStatus = data.firstNonEmptyString(keys: ["status"]) ?? "UNKNOWN"
        let normalized = rawStatus
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "-", with: "_")
        return ApplicationStatus(rawValue: normalized) ?? .unknown
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

    func firstDate(keys: [String]) -> Date? {
        for key in keys {
            if let timestamp = self[key] as? Timestamp {
                return timestamp.dateValue()
            }
            if let date = self[key] as? Date {
                return date
            }
            if let number = self[key] as? NSNumber {
                let raw = number.doubleValue
                if raw > 1_000_000_000_000 {
                    return Date(timeIntervalSince1970: raw / 1000.0)
                }
                if raw > 0 {
                    return Date(timeIntervalSince1970: raw)
                }
            }
            if let text = self[key] as? String, let raw = Double(text.trimmingCharacters(in: .whitespacesAndNewlines)) {
                if raw > 1_000_000_000_000 {
                    return Date(timeIntervalSince1970: raw / 1000.0)
                }
                if raw > 0 {
                    return Date(timeIntervalSince1970: raw)
                }
            }
        }
        return nil
    }
}
