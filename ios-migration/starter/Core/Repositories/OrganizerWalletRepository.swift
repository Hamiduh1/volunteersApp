import Foundation
import FirebaseFirestore

final class OrganizerWalletRepository {
    private let db = Firestore.firestore()

    func fetchSummary(uid: String) async throws -> WalletSummary {
        let userSnap = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let data = userSnap.data() ?? [:]
        let wallet = data["wallet"] as? [String: Any] ?? [:]

        let balance = (wallet["balance"] as? NSNumber)?.doubleValue
            ?? (wallet["availableBalance"] as? NSNumber)?.doubleValue
            ?? 0.0
        let currency = (wallet["currency"] as? String) ?? "USD"
        return WalletSummary(balance: balance, currency: currency)
    }

    func fetchTransactions(uid: String, limit: Int = 50) async throws -> [WalletTransactionRecord] {
        let snap = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.transactions.rawValue)
            .limit(to: limit)
            .getDocuments()

        let txs = snap.documents.map { doc -> WalletTransactionRecord in
            let data = doc.data()
            let amount = (data["amount"] as? NSNumber)?.doubleValue ?? 0
            let status = (data["status"] as? String) ?? "unknown"
            let type = (data["type"] as? String) ?? "transaction"
            let note = (data["description"] as? String) ?? (data["note"] as? String)
            let title = (data["title"] as? String) ?? (data["label"] as? String) ?? type

            let timestamp = data["timestamp"] as? Timestamp
            let createdAt = data["createdAt"] as? Timestamp
            let date = timestamp?.dateValue() ?? createdAt?.dateValue()

            return WalletTransactionRecord(
                id: doc.documentID,
                title: title,
                type: type,
                amount: amount,
                status: status,
                createdAt: date,
                note: note
            )
        }

        return txs.sorted {
            let l = $0.createdAt ?? .distantPast
            let r = $1.createdAt ?? .distantPast
            return l > r
        }
    }
}
