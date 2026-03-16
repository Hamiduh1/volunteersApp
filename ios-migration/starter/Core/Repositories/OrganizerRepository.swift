import Foundation
import FirebaseFirestore

struct OrganizerManagedApplicationItem: Identifiable {
    let id: String
    let eventId: String
    let applicationId: String
    let documentId: String
    let rootApplicationId: String?
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
        let queries: [Query] = [
            db.collection(FirestoreCollection.events.rawValue)
                .whereField("organizerId", isEqualTo: uid)
                .limit(to: limit),
            db.collection(FirestoreCollection.events.rawValue)
                .whereField("organizerUid", isEqualTo: uid)
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

        // Fallback when filtered queries are denied/misindexed.
        if docsByPath.isEmpty {
            do {
                let fallback = try await db.collection(FirestoreCollection.events.rawValue)
                    .limit(to: max(limit * 3, 150))
                    .getDocuments()
                for doc in fallback.documents {
                    let data = doc.data()
                    let organizerId = data.firstNonEmptyString(keys: ["organizerId", "organizerUid"]) ?? ""
                    if organizerId == uid {
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

        let events = docsByPath.values.compactMap(parseEvent)
        return events.sorted {
            let l = $0.eventDateTime?.dateValue() ?? $0.createdAt?.dateValue() ?? .distantFuture
            let r = $1.eventDateTime?.dateValue() ?? $1.createdAt?.dateValue() ?? .distantFuture
            return l < r
        }
    }

    func fetchManagedApplications(uid: String) async throws -> [OrganizerManagedApplicationItem] {
        let queries: [Query] = [
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("organizerId", isEqualTo: uid),
            db.collectionGroup(FirestoreCollectionGroup.applications.rawValue)
                .whereField("organizerUid", isEqualTo: uid)
        ]

        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        var lastError: Error?

        for query in queries {
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents where isEventApplication(path: doc.reference.path) {
                    docsByPath[doc.reference.path] = doc
                }
            } catch {
                lastError = error
            }
        }

        // Fallback: load applications under hosted events directly.
        if docsByPath.isEmpty {
            do {
                let hostedEvents = try await fetchHostedEvents(uid: uid, limit: 120)
                for event in hostedEvents {
                    let eventId = event.id ?? ""
                    guard !eventId.isEmpty else { continue }
                    do {
                        let apps = try await db.collection(FirestoreCollection.events.rawValue)
                            .document(eventId)
                            .collection(FirestoreSubcollection.applications.rawValue)
                            .limit(to: 250)
                            .getDocuments()
                        for doc in apps.documents {
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

        let eventIds = Set(docsByPath.values.compactMap { doc in
            let data = doc.data()
            let fromField = normalizeDocId(
                data.firstNonEmptyString(keys: ["eventId"]),
                collection: FirestoreCollection.events.rawValue
            )
            if !fromField.isEmpty { return fromField }
            return eventIdFromPath(doc.reference.path)
        })
        let eventMap = try await fetchEventTitles(ids: Array(eventIds))

        var items: [OrganizerManagedApplicationItem] = []
        for doc in docsByPath.values {
            let data = doc.data()
            let eventId = normalizeDocId(
                data.firstNonEmptyString(keys: ["eventId"]) ?? eventIdFromPath(doc.reference.path),
                collection: FirestoreCollection.events.rawValue
            )
            guard !eventId.isEmpty else { continue }

            let documentId = applicationDocumentIdFromPath(doc.reference.path) ?? doc.documentID
            let applicationId = data.firstNonEmptyString(keys: ["applicationId", "id"]) ?? documentId
            let volunteerId = data.firstNonEmptyString(keys: ["userId", "volunteerUid", "volunteerId", "applicantId"]) ?? ""
            let volunteerName = data.firstNonEmptyString(keys: ["volunteerName", "name", "applicantName", "userName"]) ?? "Volunteer"
            let volunteerEmail = data.firstNonEmptyString(keys: ["volunteerEmail", "email", "userEmail"]) ?? ""
            let status = parseStatus(data["status"])
            let appliedAt = data.firstDate(keys: ["appliedAt", "appliedDate", "timestamp", "createdAt", "lastUpdatedAt"])
            let rootApplicationId = data.firstNonEmptyString(keys: ["rootApplicationId"])

            items.append(
                OrganizerManagedApplicationItem(
                    id: "\(eventId)-\(documentId)-\(applicationId)",
                    eventId: eventId,
                    applicationId: applicationId,
                    documentId: documentId,
                    rootApplicationId: rootApplicationId,
                    volunteerId: volunteerId,
                    volunteerName: volunteerName,
                    volunteerEmail: volunteerEmail,
                    status: status,
                    eventTitle: eventMap[eventId] ?? "Event",
                    appliedAt: appliedAt
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
        let eventAppRef = db.collection(FirestoreCollection.events.rawValue)
            .document(eventId)
            .collection(FirestoreSubcollection.applications.rawValue)
            .document(documentId)

        try await eventAppRef.setData(
            [
                "status": status.rawValue,
                "lastUpdatedAt": FieldValue.serverTimestamp()
            ],
            merge: true
        )

        // Best-effort sync to root applications for volunteer activity parity.
        let eventAppSnap = try? await eventAppRef.getDocument()
        let eventData = eventAppSnap?.data() ?? [:]
        let volunteerCandidates = Set(
            [
                eventData.firstNonEmptyString(keys: ["userId"]),
                eventData.firstNonEmptyString(keys: ["volunteerUid"]),
                eventData.firstNonEmptyString(keys: ["volunteerId"])
            ].compactMap { $0 }
        )
        let appIdCandidates = Set(
            ([documentId] + [
                eventData.firstNonEmptyString(keys: ["applicationId"]),
                eventData.firstNonEmptyString(keys: ["rootApplicationId"]),
                eventData.firstNonEmptyString(keys: ["id"])
            ].compactMap { $0 }).filter { !$0.isEmpty }
        )

        for appId in appIdCandidates where !appId.isEmpty {
            let rootRef = db.collection(FirestoreCollection.applications.rawValue).document(appId)
            try? await rootRef.setData(
                [
                    "status": status.rawValue,
                    "lastUpdatedAt": FieldValue.serverTimestamp()
                ],
                merge: true
            )
        }

        do {
            let rootByEvent = try await db.collection(FirestoreCollection.applications.rawValue)
                .whereField("eventId", isEqualTo: eventId)
                .limit(to: 120)
                .getDocuments()

            for doc in rootByEvent.documents {
                let data = doc.data()
                let docVolunteerId = data.firstNonEmptyString(keys: ["userId", "volunteerUid", "volunteerId"])
                let docAppId = data.firstNonEmptyString(keys: ["applicationId", "id"]) ?? doc.documentID
                let matchesVolunteer = docVolunteerId.map { volunteerCandidates.contains($0) } ?? false
                let matchesAppId = appIdCandidates.contains(docAppId)
                let matchesDocId = appIdCandidates.contains(doc.documentID)
                guard matchesVolunteer || matchesAppId || matchesDocId else { continue }

                try? await doc.reference.setData(
                    [
                        "status": status.rawValue,
                        "lastUpdatedAt": FieldValue.serverTimestamp()
                    ],
                    merge: true
                )
            }
        } catch {
            // Ignore root sync failures; event application update already succeeded.
        }
    }

    func createHostedEvent(
        uid: String,
        title: String,
        description: String,
        category: String,
        locationName: String,
        locationAddress: String,
        eventDate: Date,
        volunteerLimit: Int,
        payment: Double
    ) async throws -> String {
        let organizerName = try await fetchUserDisplayName(uid: uid)
        let ref = db.collection(FirestoreCollection.events.rawValue).document()

        try await ref.setData([
            "title": title.trimmingCharacters(in: .whitespacesAndNewlines),
            "description": description.trimmingCharacters(in: .whitespacesAndNewlines),
            "category": category.trimmingCharacters(in: .whitespacesAndNewlines),
            "locationName": locationName.trimmingCharacters(in: .whitespacesAndNewlines),
            "locationAddress": locationAddress.trimmingCharacters(in: .whitespacesAndNewlines),
            "eventDateTime": Timestamp(date: eventDate),
            "eventTimestamp": Timestamp(date: eventDate),
            "volunteerLimit": volunteerLimit,
            "participantsCount": 0,
            "payment": payment,
            "eventFee": payment,
            "status": "OPEN",
            "organizerId": uid,
            "organizerUid": uid,
            "organizerName": organizerName,
            "createdAt": FieldValue.serverTimestamp(),
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ], merge: true)

        return ref.documentID
    }

    func updateHostedEvent(
        eventId: String,
        uid: String,
        title: String,
        description: String,
        category: String,
        locationName: String,
        locationAddress: String,
        eventDate: Date,
        volunteerLimit: Int,
        payment: Double
    ) async throws {
        let organizerName = try await fetchUserDisplayName(uid: uid)
        let ref = db.collection(FirestoreCollection.events.rawValue).document(eventId)

        try await ref.setData([
            "title": title.trimmingCharacters(in: .whitespacesAndNewlines),
            "description": description.trimmingCharacters(in: .whitespacesAndNewlines),
            "category": category.trimmingCharacters(in: .whitespacesAndNewlines),
            "locationName": locationName.trimmingCharacters(in: .whitespacesAndNewlines),
            "locationAddress": locationAddress.trimmingCharacters(in: .whitespacesAndNewlines),
            "eventDateTime": Timestamp(date: eventDate),
            "eventTimestamp": Timestamp(date: eventDate),
            "volunteerLimit": volunteerLimit,
            "payment": payment,
            "eventFee": payment,
            "organizerId": uid,
            "organizerUid": uid,
            "organizerName": organizerName,
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ], merge: true)
    }

    func deleteHostedEvent(eventId: String) async throws {
        let ref = db.collection(FirestoreCollection.events.rawValue).document(eventId)
        do {
            try await ref.delete()
        } catch {
            try await ref.setData([
                "isDeleted": true,
                "status": "CANCELLED",
                "lastUpdatedAt": FieldValue.serverTimestamp()
            ], merge: true)
        }
    }

    private func fetchEventTitles(ids: [String]) async throws -> [String: String] {
        var out: [String: String] = [:]
        for id in ids where !id.isEmpty {
            do {
                let snap = try await db.collection(FirestoreCollection.events.rawValue).document(id).getDocument()
                let title = (snap.data() ?? [:]).firstNonEmptyString(keys: ["title", "name"]) ?? "Event"
                out[id] = title
            } catch {
                continue
            }
        }
        return out
    }

    private func fetchUserDisplayName(uid: String) async throws -> String {
        let userSnap = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let data = userSnap.data() ?? [:]
        return data.firstNonEmptyString(keys: ["name", "username", "email"]) ?? "Organizer"
    }

    private func parseEvent(_ doc: QueryDocumentSnapshot) -> EventRecord? {
        let data = doc.data()
        if data.truthy(keys: ["isDeleted"]) { return nil }

        return EventRecord(
            id: doc.documentID,
            title: data.firstNonEmptyString(keys: ["title", "name"]),
            description: data.firstNonEmptyString(keys: ["description", "details"]),
            category: data.firstNonEmptyString(keys: ["category"]),
            eventDateTime: data.firstTimestamp(keys: ["eventDateTime", "eventTimestamp", "timestamp", "date"]),
            locationName: data.firstNonEmptyString(keys: ["locationName", "location"]),
            locationAddress: data.firstNonEmptyString(keys: ["locationAddress", "address"]),
            payment: data.firstDouble(keys: ["payment", "eventFee", "amount"]),
            eventFee: data.firstDouble(keys: ["eventFee", "payment", "amount"]),
            volunteerLimit: data.firstInt(keys: ["volunteerLimit", "maxVolunteers", "slots"]),
            participantsCount: data.firstInt(keys: ["participantsCount", "appliedCount", "attendeesCount"]),
            requirements: data.firstNonEmptyString(keys: ["requirements"]),
            contactInfo: data.firstNonEmptyString(keys: ["contactInfo", "contact", "organizerContact"]),
            status: data.firstNonEmptyString(keys: ["status"]),
            organizerId: data.firstNonEmptyString(keys: ["organizerId"]),
            organizerUid: data.firstNonEmptyString(keys: ["organizerUid"]),
            organizerName: data.firstNonEmptyString(keys: ["organizerName", "hostName"]),
            createdAt: data.firstTimestamp(keys: ["createdAt"]),
            lastUpdatedAt: data.firstTimestamp(keys: ["lastUpdatedAt", "updatedAt"])
        )
    }

    private func parseStatus(_ raw: Any?) -> ApplicationStatus {
        let value = (raw as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "-", with: "_")
        return ApplicationStatus(rawValue: value ?? "") ?? .unknown
    }

    private func isEventApplication(path: String) -> Bool {
        let segments = path.split(separator: "/").map(String.init)
        return segments.count >= 4
            && segments[0] == FirestoreCollection.events.rawValue
            && segments[2] == FirestoreSubcollection.applications.rawValue
    }

    private func eventIdFromPath(_ path: String) -> String? {
        let segments = path.split(separator: "/").map(String.init)
        guard segments.count >= 2, segments[0] == FirestoreCollection.events.rawValue else { return nil }
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
            if let value = self[key] as? Timestamp {
                return value
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

    func firstDouble(keys: [String]) -> Double? {
        for key in keys {
            if let number = self[key] as? NSNumber {
                return number.doubleValue
            }
            if let text = self[key] as? String {
                let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: "")
                if let value = Double(cleaned) {
                    return value
                }
            }
        }
        return nil
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
