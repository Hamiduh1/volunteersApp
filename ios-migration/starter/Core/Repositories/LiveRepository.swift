import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class LiveRepository {
    private let db = Firestore.firestore()

    func fetchLiveSessions(limit: Int = 100) async throws -> [LiveSessionRecord] {
        let snapshot = try await db.collection(FirestoreCollection.liveSessions.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: LiveSessionRecord.self)
        }
        .sorted {
            let l = $0.createdAt?.dateValue() ?? .distantPast
            let r = $1.createdAt?.dateValue() ?? .distantPast
            return l > r
        }
    }

    func getRtcToken(channelName: String, uid: String) async throws -> String {
        let response = try await FunctionsService.shared.callMap(
            function: .getAgoraRtcToken,
            data: [
                "channelName": channelName,
                "uid": uid
            ]
        )

        if let token = response["token"] as? String, !token.isEmpty {
            return token
        }
        if let data = response["data"] as? [String: Any],
           let token = data["token"] as? String,
           !token.isEmpty {
            return token
        }
        return ""
    }
}
