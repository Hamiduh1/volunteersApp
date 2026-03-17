import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift
import FirebaseFunctions

enum LiveRepositoryError: LocalizedError {
    case missingChannel
    case missingUid
    case tokenNotFound
    case tokenFunctionNotFound
    case functionsFailure(String)

    var errorDescription: String? {
        switch self {
        case .missingChannel:
            return "Session channel is missing."
        case .missingUid:
            return "Current user id is missing."
        case .tokenNotFound:
            return "Token response was empty."
        case .tokenFunctionNotFound:
            return "Token function is not deployed. Deploy Cloud Functions and try again."
        case .functionsFailure(let message):
            return message
        }
    }
}

final class LiveRepository {
    private let db = Firestore.firestore()

    func fetchLiveSessions(limit: Int = 100) async throws -> [LiveSessionRecord] {
        let snapshot = try await db.collection(FirestoreCollection.liveSessions.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap(parseLiveSession)
        .sorted {
            let l = $0.createdAt?.dateValue() ?? .distantPast
            let r = $1.createdAt?.dateValue() ?? .distantPast
            return l > r
        }
    }

    func getRtcToken(channelName: String, uid: String) async throws -> String {
        let cleanChannel = channelName.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanUid = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanChannel.isEmpty else { throw LiveRepositoryError.missingChannel }
        guard !cleanUid.isEmpty else { throw LiveRepositoryError.missingUid }

        do {
            let result = try await FunctionsService.shared.call(
                function: .getAgoraRtcToken,
                data: [
                    "channelName": cleanChannel,
                    "uid": cleanUid
                ]
            )
            if let token = parseToken(from: result), !token.isEmpty {
                return token
            }
            throw LiveRepositoryError.tokenNotFound
        } catch let error as LiveRepositoryError {
            throw error
        } catch let nsError as NSError {
            if nsError.domain == FunctionsErrorDomain,
               nsError.code == FunctionsErrorCode.notFound.rawValue {
                throw LiveRepositoryError.tokenFunctionNotFound
            }
            if let message = nsError.userInfo[NSLocalizedDescriptionKey] as? String,
               !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                throw LiveRepositoryError.functionsFailure(message)
            }
            throw LiveRepositoryError.functionsFailure(nsError.localizedDescription)
        }
    }

    func startLiveSession(hostUid: String, hostName: String, title: String) async throws -> LiveSessionRecord {
        let cleanHostUid = hostUid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanHostUid.isEmpty else { throw LiveRepositoryError.missingUid }

        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalTitle = cleanTitle.isEmpty ? "Live Session" : cleanTitle
        let channel = "live-\(cleanHostUid)-\(Int(Date().timeIntervalSince1970))"

        let ref = db.collection(FirestoreCollection.liveSessions.rawValue).document()
        try await ref.setData([
            "agoraChannelName": channel,
            "channelName": channel,
            "hostId": cleanHostUid,
            "hostUid": cleanHostUid,
            "hostName": hostName,
            "title": finalTitle,
            "status": "LIVE",
            "createdAt": FieldValue.serverTimestamp(),
            "startTime": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ], merge: true)

        let created = try await ref.getDocument()
        if let data = created.data(),
           let parsed = parseLiveSessionSnapshot(documentId: ref.documentID, data: data) {
            return parsed
        }
        return LiveSessionRecord(
            id: ref.documentID,
            agoraChannelName: channel,
            hostId: cleanHostUid,
            hostName: hostName,
            title: finalTitle,
            status: "LIVE",
            createdAt: Timestamp(date: Date())
        )
    }

    func endLiveSession(sessionId: String, hostUid: String) async throws {
        let cleanSessionId = sessionId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanSessionId.isEmpty else { return }

        let ref = db.collection(FirestoreCollection.liveSessions.rawValue).document(cleanSessionId)
        let snap = try await ref.getDocument()
        let hostId = (snap.data() ?? [:]).firstNonEmptyString(keys: ["hostId", "hostUid"])
        let cleanHostUid = hostUid.trimmingCharacters(in: .whitespacesAndNewlines)
        if let hostId, !hostId.isEmpty, hostId != cleanHostUid {
            throw LiveRepositoryError.functionsFailure("Only the host can end this live session.")
        }

        try await ref.setData([
            "status": "ENDED",
            "endedAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ], merge: true)
    }

    private func parseLiveSession(_ doc: QueryDocumentSnapshot) -> LiveSessionRecord? {
        let data = doc.data()
        return parseLiveSessionSnapshot(documentId: doc.documentID, data: data)
    }

    private func parseLiveSessionSnapshot(documentId: String, data: [String: Any]) -> LiveSessionRecord? {
        if let decoded = try? Firestore.Decoder().decode(LiveSessionRecord.self, from: data) {
            return LiveSessionRecord(
                id: decoded.id ?? documentId,
                agoraChannelName: decoded.agoraChannelName,
                hostId: decoded.hostId,
                hostName: decoded.hostName,
                title: decoded.title,
                status: decoded.status,
                createdAt: decoded.createdAt
            )
        }

        let channel = data.firstNonEmptyString(keys: ["agoraChannelName", "channelName", "agoraChannel", "streamChannel", "channel"])
        let hostName = data.firstNonEmptyString(keys: ["hostName", "hostDisplayName", "hostUsername", "hostEmail"])
        let hostId = data.firstNonEmptyString(keys: ["hostId", "hostUid", "organizerId", "userId"])
        let title = data.firstNonEmptyString(keys: ["title", "sessionTitle", "name"]) ?? "Untitled Session"
        let status = data.firstNonEmptyString(keys: ["status", "state"]) ?? "unknown"
        let createdAtDate = data.firstDate(keys: [
            "createdAt",
            "timestamp",
            "startTime",
            "startedAt",
            "createdAtMs",
            "timestampMs"
        ])
        let createdAtTimestamp = createdAtDate.map { Timestamp(date: $0) }

        return LiveSessionRecord(
            id: documentId,
            agoraChannelName: channel,
            hostId: hostId,
            hostName: hostName,
            title: title,
            status: status,
            createdAt: createdAtTimestamp
        )
    }

    private func parseToken(from result: Any?) -> String? {
        if let token = (result as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !token.isEmpty {
            return token
        }

        guard let map = result as? [String: Any] else { return nil }
        for key in ["token", "rtcToken", "agoraRtcToken", "agora_token"] {
            if let token = (map[key] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines),
               !token.isEmpty {
                return token
            }
        }
        if let nested = map["data"] as? [String: Any],
           let token = parseToken(from: nested) {
            return token
        }
        if let nested = map["result"] as? [String: Any],
           let token = parseToken(from: nested) {
            return token
        }
        return nil
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
        }
        return nil
    }
}
