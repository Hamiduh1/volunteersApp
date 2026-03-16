import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class GlobalWalletRepository {
    private let db = Firestore.firestore()

    func fetchSummary(uid: String) async throws -> WalletSummary {
        let userSnap = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let data = userSnap.data() ?? [:]
        let wallet = data["wallet"] as? [String: Any] ?? [:]

        let balance = (wallet["balance"] as? NSNumber)?.doubleValue
            ?? (wallet["availableBalance"] as? NSNumber)?.doubleValue
            ?? (data["walletBalance"] as? NSNumber)?.doubleValue
            ?? 0.0
        let currency = (wallet["currency"] as? String)
            ?? (data["walletCurrency"] as? String)
            ?? "USD"
        return WalletSummary(balance: balance, currency: currency)
    }

    func fetchTransactions(uid: String, limit: Int = 80) async throws -> [WalletTransactionRecord] {
        let snap = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.transactions.rawValue)
            .limit(to: limit)
            .getDocuments()

        let transactions = snap.documents.map { doc -> WalletTransactionRecord in
            let data = doc.data()
            let amount = (data["amount"] as? NSNumber)?.doubleValue ?? 0
            let status = (data["status"] as? String) ?? "unknown"
            let type = (data["type"] as? String) ?? "transaction"
            let note = (data["description"] as? String) ?? (data["note"] as? String)
            let title = (data["title"] as? String) ?? (data["label"] as? String) ?? type
            let timestamp = data["timestamp"] as? Timestamp
            let createdAt = data["createdAt"] as? Timestamp
            let lastUpdatedAt = data["lastUpdatedAt"] as? Timestamp
            let processedAt = data["processedAt"] as? Timestamp
            return WalletTransactionRecord(
                id: doc.documentID,
                title: title,
                type: type,
                amount: amount,
                status: status,
                createdAt: timestamp?.dateValue()
                    ?? createdAt?.dateValue()
                    ?? lastUpdatedAt?.dateValue()
                    ?? processedAt?.dateValue(),
                note: note
            )
        }

        return transactions.sorted {
            let l = $0.createdAt ?? .distantPast
            let r = $1.createdAt ?? .distantPast
            return l > r
        }
    }

    func fetchBeneficiaries(uid: String, limit: Int = 60) async throws -> [BeneficiaryRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.beneficiaries.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: BeneficiaryRecord.self)
        }
    }

    func fetchPaymentMethods(uid: String, limit: Int = 60) async throws -> [PaymentMethodRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.paymentMethods.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: PaymentMethodRecord.self)
        }
    }

    func getQuote(
        amount: Double,
        fromCurrency: String,
        toCurrency: String
    ) async throws -> WalletQuoteRecord {
        let map = try await FunctionsService.shared.callMap(
            function: .getSecureExchangeRate,
            data: [
                "amount": amount,
                "fromCurrency": fromCurrency,
                "toCurrency": toCurrency
            ]
        )
        let data = (map["data"] as? [String: Any]) ?? map

        let rate = data.double("rate", fallback: 1.0)
        let recipientAmount = data.double("recipientAmount", fallback: data.double("convertedAmount", fallback: amount * rate))
        let sourceAmount = data.double("sourceAmount", fallback: amount)
        let sourceCurrency = data.string("sourceCurrency") ?? fromCurrency
        let targetCurrency = data.string("targetCurrency") ?? toCurrency

        return WalletQuoteRecord(
            rate: rate,
            recipientAmount: recipientAmount,
            sourceAmount: sourceAmount,
            sourceCurrency: sourceCurrency,
            targetCurrency: targetCurrency
        )
    }

    func sendToAppUser(
        recipientUserId: String,
        amount: Double,
        currency: String,
        note: String
    ) async throws -> String {
        let map = try await FunctionsService.shared.callMap(
            function: .initiateTransfer,
            data: [
                "destinationType": "app_user",
                "recipientUserId": recipientUserId,
                "amount": amount,
                "currency": currency,
                "note": note
            ]
        )
        return (map["message"] as? String) ?? ((map["data"] as? [String: Any])?["message"] as? String) ?? "Transfer submitted."
    }

    func sendToBeneficiary(
        beneficiaryId: String,
        amount: Double,
        currency: String,
        note: String
    ) async throws -> String {
        let map = try await FunctionsService.shared.callMap(
            function: .initiateTransfer,
            data: [
                "destinationType": "beneficiary",
                "beneficiaryId": beneficiaryId,
                "amount": amount,
                "currency": currency,
                "note": note
            ]
        )
        return (map["message"] as? String) ?? ((map["data"] as? [String: Any])?["message"] as? String) ?? "Transfer submitted."
    }

    func sendToPaymentMethod(
        paymentMethodId: String,
        amount: Double,
        currency: String,
        note: String
    ) async throws -> String {
        let map = try await FunctionsService.shared.callMap(
            function: .initiateTransfer,
            data: [
                "destinationType": "payment_method",
                "paymentMethodId": paymentMethodId,
                "amount": amount,
                "currency": currency,
                "note": note
            ]
        )
        return (map["message"] as? String) ?? ((map["data"] as? [String: Any])?["message"] as? String) ?? "Transfer submitted."
    }
}

private extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        (self[key] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nonEmpty
    }

    func double(_ key: String, fallback: Double = 0) -> Double {
        (self[key] as? NSNumber)?.doubleValue ?? fallback
    }
}

private extension Optional where Wrapped == String {
    var nonEmpty: String? {
        guard let self, !self.isEmpty else { return nil }
        return self
    }
}
