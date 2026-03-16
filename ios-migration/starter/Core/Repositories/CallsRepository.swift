import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class CallsRepository {
    private let db = Firestore.firestore()

    func fetchCallLogs(uid: String, limit: Int = 150) async throws -> [CallLogRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.callLogs.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: CallLogRecord.self)
        }
        .sorted {
            let l = $0.startedAt?.dateValue() ?? .distantPast
            let r = $1.startedAt?.dateValue() ?? .distantPast
            return l > r
        }
    }
}
