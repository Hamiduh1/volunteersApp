import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class AlertsRepository {
    private let db = Firestore.firestore()

    func fetchNotificationSettings(uid: String) async throws -> NotificationSettingsRecord {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreCollection.settings.rawValue)
            .document("notifications")
            .getDocument()

        guard let data = snapshot.data() else {
            return NotificationSettingsRecord()
        }

        return NotificationSettingsRecord(
            newFollowers: data.bool("newFollowers", fallback: true),
            jokesPosts: data.bool("jokesPosts", fallback: true),
            liveStreams: data.bool("liveStreams", fallback: true),
            eventReminders: data.bool("eventReminders", fallback: true),
            appUpdates: data.bool("appUpdates", fallback: false)
        )
    }

    func updateNotificationSetting(uid: String, field: NotificationSettingField, enabled: Bool) async throws {
        try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreCollection.settings.rawValue)
            .document("notifications")
            .setData(
                [
                    field.rawValue: enabled,
                    "updatedAt": FieldValue.serverTimestamp()
                ],
                merge: true
            )
    }

    func fetchCommunityAlerts(uid: String, limit: Int = 40) async throws -> [CommunityAlertRecord] {
        async let invitationAlerts = fetchInvitationAlerts(uid: uid, limit: limit)
        async let liveAlerts = fetchLiveSessionAlerts(limit: min(15, max(5, limit / 3)))
        async let configAlerts = fetchConfiguredCommunityAlerts(limit: min(15, max(5, limit / 3)))

        var merged: [CommunityAlertRecord] = []
        merged.append(contentsOf: (try? await invitationAlerts) ?? [])
        merged.append(contentsOf: (try? await liveAlerts) ?? [])
        merged.append(contentsOf: (try? await configAlerts) ?? [])

        var byId: [String: CommunityAlertRecord] = [:]
        for alert in merged {
            byId[alert.id] = alert
        }
        return byId.values
            .sorted { $0.timestamp > $1.timestamp }
            .prefix(limit)
            .map { $0 }
    }

    private func fetchInvitationAlerts(uid: String, limit: Int) async throws -> [CommunityAlertRecord] {
        async let direct = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.invitations.rawValue)
            .limit(to: limit)
            .getDocuments()

        async let legacy = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.chatInvitations.rawValue)
            .limit(to: limit)
            .getDocuments()

        async let blindDate = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.blindDateInvitations.rawValue)
            .limit(to: limit)
            .getDocuments()

        let snapshots = try await [direct, legacy, blindDate]
        var alerts: [CommunityAlertRecord] = []

        for snapshot in snapshots {
            for doc in snapshot.documents {
                let data = doc.data()
                let status = data.string("status")?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? "pending"
                if status != "pending" && status != "new" {
                    continue
                }

                let senderName =
                    data.string("senderName")
                    ?? data.string("inviterName")
                    ?? "Community Member"
                let context =
                    data.string("context")
                    ?? data.string("source")
                    ?? "Chat invitation"
                let imageUrl =
                    data.string("senderProfileImageUrl")
                    ?? data.string("senderProfilePicUrl")
                    ?? data.string("senderAvatarUrl")
                let timestamp =
                    data.timestamp(keys: ["timestamp", "updatedAt", "createdAt", "sentAt"])?.dateValue()
                    ?? Date()

                alerts.append(
                    CommunityAlertRecord(
                        id: "invite:\(doc.reference.path)",
                        title: "New invitation from \(senderName)",
                        description: context,
                        source: "invitation",
                        imageUrl: imageUrl,
                        timestamp: timestamp
                    )
                )
            }
        }

        return alerts
    }

    private func fetchLiveSessionAlerts(limit: Int) async throws -> [CommunityAlertRecord] {
        let snapshot = try await db.collection(FirestoreCollection.liveSessions.rawValue)
            .whereField("status", in: ["LIVE", "live", "active", "ACTIVE"])
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.map { doc in
            let data = doc.data()
            let title = data.string("title") ?? "Live session"
            let hostName = data.string("hostName") ?? "Community host"
            let timestamp =
                data.timestamp(keys: ["createdAt", "startTime", "updatedAt"])?.dateValue()
                ?? Date()
            return CommunityAlertRecord(
                id: "live:\(doc.documentID)",
                title: "Live now: \(title)",
                description: "Hosted by \(hostName)",
                source: "live",
                imageUrl: nil,
                timestamp: timestamp
            )
        }
    }

    private func fetchConfiguredCommunityAlerts(limit: Int) async throws -> [CommunityAlertRecord] {
        let snapshot = try await db.collection(FirestoreCollection.appConfig.rawValue)
            .document("community_alerts")
            .getDocument()

        guard let data = snapshot.data(),
              let items = data["items"] as? [[String: Any]] else {
            return []
        }

        return items.prefix(limit).compactMap { item in
            let title = (item["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let description = (item["description"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !title.isEmpty || !description.isEmpty else { return nil }

            let rawTimestamp = item["timestamp"]
            let timestamp: Date
            if let ts = rawTimestamp as? Timestamp {
                timestamp = ts.dateValue()
            } else if let date = rawTimestamp as? Date {
                timestamp = date
            } else {
                timestamp = Date()
            }

            return CommunityAlertRecord(
                id: "config:\((item["id"] as? String) ?? UUID().uuidString)",
                title: title.isEmpty ? "Community alert" : title,
                description: description.isEmpty ? "Stay connected with Volunteers App updates." : description,
                source: "community_config",
                imageUrl: item["imageUrl"] as? String,
                timestamp: timestamp
            )
        }
    }
}

private extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        guard let value = self[key] as? String else { return nil }
        let clean = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? nil : clean
    }

    func bool(_ key: String, fallback: Bool) -> Bool {
        if let value = self[key] as? Bool {
            return value
        }
        if let value = self[key] as? NSNumber {
            return value.boolValue
        }
        return fallback
    }

    func timestamp(keys: [String]) -> Timestamp? {
        for key in keys {
            if let timestamp = self[key] as? Timestamp {
                return timestamp
            }
            if let date = self[key] as? Date {
                return Timestamp(date: date)
            }
            if let num = self[key] as? NSNumber {
                let raw = num.doubleValue
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
