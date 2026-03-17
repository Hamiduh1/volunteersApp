import Foundation
import FirebaseFirestore

final class OrganizerWalletRepository {
    private let db = Firestore.firestore()

    func fetchSummary(uid: String) async throws -> WalletSummary {
        let userSnap = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let data = userSnap.data() ?? [:]
        let wallet = data["wallet"] as? [String: Any] ?? [:]

        let balance = wallet.double(keys: ["balance", "availableBalance", "currentBalance"])
            ?? data.double(keys: ["walletBalance", "availableBalance", "balance"])
            ?? 0.0
        let currency = wallet.string(keys: ["currency"])
            ?? data.string(keys: ["walletCurrency", "currency"])
            ?? "USD"
        return WalletSummary(balance: balance, currency: currency)
    }

    func fetchTransactions(uid: String, limit: Int = 50) async throws -> [WalletTransactionRecord] {
        let collection = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.transactions.rawValue)

        let queries: [Query] = [
            collection.order(by: "timestamp", descending: true).limit(to: limit),
            collection.order(by: "createdAt", descending: true).limit(to: limit),
            collection.order(by: "lastUpdatedAt", descending: true).limit(to: limit),
            collection.limit(to: limit)
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

        if docsByPath.isEmpty, let lastError {
            throw lastError
        }

        let txs = docsByPath.values.map(parseTransaction)

        return txs.sorted {
            let l = $0.createdAt ?? .distantPast
            let r = $1.createdAt ?? .distantPast
            return l > r
        }
        .prefix(limit)
        .map { $0 }
    }

    private func parseTransaction(_ doc: QueryDocumentSnapshot) -> WalletTransactionRecord {
        let data = doc.data()
        let amount = data.double(keys: ["amount", "transactionAmount", "value", "netAmount"]) ?? 0
        let status = data.string(keys: ["status", "state"]) ?? "unknown"
        let type = data.string(keys: ["type", "transactionType", "source"]) ?? "transaction"
        let source = data.string(keys: ["source", "fundingSourceType", "destinationType"])
        let note = data.string(keys: ["description", "note", "memo", "message", "reason"])
        let title = data.string(keys: ["title", "label", "name"]) ?? type
        let date = data.date(keys: ["timestamp", "createdAt", "lastUpdatedAt", "processedAt", "updatedAt"])

        return WalletTransactionRecord(
            id: doc.documentID,
            title: title,
            type: type,
            amount: amount,
            status: status,
            source: source,
            createdAt: date,
            note: note
        )
    }
}

private extension Dictionary where Key == String, Value == Any {
    func string(keys: [String]) -> String? {
        for key in keys {
            if let value = self[key] as? String {
                let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cleaned.isEmpty { return cleaned }
            }
        }
        return nil
    }

    func double(keys: [String]) -> Double? {
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

    func date(keys: [String]) -> Date? {
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
            if let text = self[key] as? String {
                let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if let raw = Double(cleaned) {
                    if raw > 1_000_000_000_000 {
                        return Date(timeIntervalSince1970: raw / 1000.0)
                    }
                    if raw > 0 {
                        return Date(timeIntervalSince1970: raw)
                    }
                }
            }
        }
        return nil
    }
}
