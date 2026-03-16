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

        let userLogs = userSnapshot.documents.compactMap { doc in
            try? doc.data(as: CallLogRecord.self)
        }
        .sorted {
            let l = $0.startedAt?.dateValue() ?? .distantPast
            let r = $1.startedAt?.dateValue() ?? .distantPast
            return l > r
        }

        if !userLogs.isEmpty {
            return userLogs
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
                let data = doc.data()
                let callerId = data["callerId"] as? String
                let receiverId = data["receiverId"] as? String
                let callType = ((data["callType"] as? String) ?? (data["type"] as? String) ?? "audio")
                let status = (data["status"] as? String) ?? "unknown"
                let direction = resolveDirection(
                    currentUid: uid,
                    callerId: callerId,
                    receiverId: receiverId,
                    status: status
                )
                let peerUid = resolvePeerUid(currentUid: uid, callerId: callerId, receiverId: receiverId)
                let peerName = try await resolvePeerName(uid: peerUid)

                let startedAt = (data["startedAt"] as? Timestamp) ?? (data["timestamp"] as? Timestamp)
                let endedAt = data["endedAt"] as? Timestamp
                let durationSec = (data["durationSec"] as? Int) ?? (data["durationSeconds"] as? Int)

                logs.append(
                    CallLogRecord(
                        id: doc.documentID,
                        chatId: chatId,
                        callerId: callerId,
                        receiverId: receiverId,
                        peerUid: peerUid,
                        peerName: peerName,
                        type: callType,
                        callType: callType,
                        direction: direction,
                        status: status,
                        startedAt: startedAt,
                        endedAt: endedAt,
                        durationSec: durationSec,
                        durationSeconds: durationSec
                    )
                )
            }
        }

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
