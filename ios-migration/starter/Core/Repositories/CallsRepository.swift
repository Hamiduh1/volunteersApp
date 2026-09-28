import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class CallsRepository {
    private let db = Firestore.firestore()
    private var userNameCache: [String: String] = [:]

    func fetchCallLogs(uid: String, limit: Int = 150) async throws -> [CallLogRecord] {
        let userSnapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.callLogs.rawValue)
            .limit(to: limit)
            .getDocuments()

        var userLogs: [CallLogRecord] = []
        for doc in userSnapshot.documents {
            if let decoded = try? doc.data(as: CallLogRecord.self) {
                userLogs.append(decoded)
                continue
            }

            if let fallback = await parseCallLog(
                data: doc.data(),
                docId: doc.documentID,
                currentUid: uid,
                chatId: nil
            ) {
                userLogs.append(fallback)
            }
        }

        if !userLogs.isEmpty {
            return dedupeAndSort(userLogs)
        }

        return try await fetchCallLogsFromChats(uid: uid, limit: limit)
    }

    private func fetchCallLogsFromChats(uid: String, limit: Int) async throws -> [CallLogRecord] {
        let chatsSnapshot = try await db.collection(FirestoreCollection.chats.rawValue)
            .whereField("participants", arrayContains: uid)
            .limit(to: 100)
            .getDocuments()

        var logs: [CallLogRecord] = []

        for chatDoc in chatsSnapshot.documents {
            let chatId = chatDoc.documentID
            let callLogsSnapshot = try await db.collection(FirestoreCollection.chats.rawValue)
                .document(chatId)
                .collection(FirestoreSubcollection.callLogs.rawValue)
                .limit(to: limit)
                .getDocuments()

            for doc in callLogsSnapshot.documents {
                if let parsed = await parseCallLog(
                    data: doc.data(),
                    docId: doc.documentID,
                    currentUid: uid,
                    chatId: chatId
                ) {
                    logs.append(parsed)
                }
            }
        }

        return dedupeAndSort(logs)
    }

    private func resolveDirection(currentUid: String, callerId: String?, receiverId: String?, status: String) -> String {
        let normalizedStatus = status.lowercased()
        if normalizedStatus == "missed", receiverId == currentUid {
            return "missed"
        }
        if callerId == currentUid {
            return "outgoing"
        }
        return "incoming"
    }

    private func resolvePeerUid(currentUid: String, callerId: String?, receiverId: String?) -> String? {
        if callerId == currentUid { return receiverId }
        if receiverId == currentUid { return callerId }
        return callerId ?? receiverId
    }

    private func resolvePeerName(uid: String?) async throws -> String {
        guard let uid, !uid.isEmpty else { return "Unknown user" }
        if let cached = userNameCache[uid] { return cached }

        let userSnap = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .getDocument()
        let data = userSnap.data() ?? [:]
        let resolved = (data["name"] as? String)
            ?? (data["username"] as? String)
            ?? (data["email"] as? String)
            ?? "Unknown user"
        userNameCache[uid] = resolved
        return resolved
    }

    private func parseCallLog(
        data: [String: Any],
        docId: String,
        currentUid: String,
        chatId: String?
    ) async -> CallLogRecord? {
        let callerId = data.firstNonEmptyString(keys: ["callerId", "fromUid", "initiatorUid"])
        let receiverId = data.firstNonEmptyString(keys: ["receiverId", "toUid", "targetUid"])
        let type = (data.firstNonEmptyString(keys: ["type", "callType"]) ?? "audio").lowercased()
        let status = (data.firstNonEmptyString(keys: ["status"]) ?? "unknown").lowercased()
        let rawDirection = data.firstNonEmptyString(keys: ["direction"])?.lowercased()
        let direction = rawDirection ?? resolveDirection(
            currentUid: currentUid,
            callerId: callerId,
            receiverId: receiverId,
            status: status
        )

        let peerUid = data.firstNonEmptyString(keys: ["peerUid"]) ?? resolvePeerUid(
            currentUid: currentUid,
            callerId: callerId,
            receiverId: receiverId
        )

        let peerNameFromPayload = data.firstNonEmptyString(keys: ["peerName", "calleeName", "callerName"])
        let peerName: String
        if let peerNameFromPayload, !peerNameFromPayload.isEmpty {
            peerName = peerNameFromPayload
        } else {
            peerName = (try? await resolvePeerName(uid: peerUid)) ?? "Unknown user"
        }

        let startedAt = data.firstTimestamp(keys: ["startedAt", "timestamp", "createdAt", "startTime"])
        let endedAt = data.firstTimestamp(keys: ["endedAt", "finishedAt", "endTime"])
        let durationSec = data.firstInt(keys: ["durationSec", "durationSeconds", "duration"])

        return CallLogRecord(
            id: docId,
            chatId: chatId,
            callerId: callerId,
            receiverId: receiverId,
            peerUid: peerUid,
            peerName: peerName,
            type: type,
            callType: type,
            direction: direction,
            status: status,
            startedAt: startedAt,
            endedAt: endedAt,
            durationSec: durationSec,
            durationSeconds: durationSec
        )
    }

    private func dedupeAndSort(_ logs: [CallLogRecord]) -> [CallLogRecord] {
        let deduped = Dictionary(
            logs.map { (key(for: $0), $0) },
            uniquingKeysWith: { first, second in
                let lhs = first.startedAt?.dateValue() ?? .distantPast
                let rhs = second.startedAt?.dateValue() ?? .distantPast
                return rhs > lhs ? second : first
            }
        ).values

        return deduped.sorted {
            let l = $0.startedAt?.dateValue() ?? .distantPast
            let r = $1.startedAt?.dateValue() ?? .distantPast
            return l > r
        }
    }

    private func key(for log: CallLogRecord) -> String {
        if let chatId = log.chatId, let id = log.id, !chatId.isEmpty, !id.isEmpty {
            return "\(chatId):\(id)"
        }
        let caller = log.callerId ?? "na"
        let receiver = log.receiverId ?? "na"
        let startedAt = log.startedAt?.dateValue().timeIntervalSince1970 ?? 0
        return "\(caller):\(receiver):\(Int(startedAt))"
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

    func firstInt(keys: [String]) -> Int? {
        for key in keys {
            if let value = self[key] as? Int {
                return value
            }
            if let value = self[key] as? NSNumber {
                return value.intValue
            }
            if let text = self[key] as? String, let value = Int(text.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return value
            }
        }
        return nil
    }
}
