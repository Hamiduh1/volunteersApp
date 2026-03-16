import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class GlobalWalletRepository {
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

    func fetchTransactions(uid: String, limit: Int = 80) async throws -> [WalletTransactionRecord] {
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

        let transactions = docsByPath.values.map(parseTransaction)

        return transactions.sorted {
            let l = $0.createdAt ?? .distantPast
            let r = $1.createdAt ?? .distantPast
            return l > r
        }
        .prefix(limit)
        .map { $0 }
    }

    func fetchBeneficiaries(uid: String, limit: Int = 60) async throws -> [BeneficiaryRecord] {
        let collection = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.beneficiaries.rawValue)

        let queries: [Query] = [
            collection.order(by: "createdAt", descending: true).limit(to: limit),
            collection.limit(to: limit)
        ]

        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        for query in queries {
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents {
                    docsByPath[doc.reference.path] = doc
                }
                if !docsByPath.isEmpty { break }
            } catch {
                continue
            }
        }

        return docsByPath.values
            .compactMap(parseBeneficiary)
            .sorted { ($0.name ?? "").localizedCaseInsensitiveCompare($1.name ?? "") == .orderedAscending }
    }

    func fetchPaymentMethods(uid: String, limit: Int = 60) async throws -> [PaymentMethodRecord] {
        let collection = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.paymentMethods.rawValue)

        let queries: [Query] = [
            collection.order(by: "createdAt", descending: true).limit(to: limit),
            collection.order(by: "timestamp", descending: true).limit(to: limit),
            collection.limit(to: limit)
        ]

        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        for query in queries {
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents {
                    docsByPath[doc.reference.path] = doc
                }
                if !docsByPath.isEmpty { break }
            } catch {
                continue
            }
        }

        return docsByPath.values
            .compactMap(parsePaymentMethod)
            .sorted { ($0.brand ?? $0.type ?? "").localizedCaseInsensitiveCompare($1.brand ?? $1.type ?? "") == .orderedAscending }
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

        let rate = data.double(keys: ["rate", "exchangeRate"]) ?? 1.0
        let recipientAmount = data.double(keys: ["recipientAmount", "convertedAmount", "targetAmount"]) ?? (amount * rate)
        let sourceAmount = data.double(keys: ["sourceAmount", "amount"]) ?? amount
        let sourceCurrency = data.string(keys: ["sourceCurrency", "fromCurrency"]) ?? fromCurrency
        let targetCurrency = data.string(keys: ["targetCurrency", "toCurrency"]) ?? toCurrency

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
        let recipientId = recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !recipientId.isEmpty else {
            throw WalletTransferError.invalidRecipient
        }

        var payload: [String: Any] = [
            "recipientId": recipientId,
            "amount": amount,
            "fundingSourceType": "WALLET",
            "destinationType": "WALLET"
        ]
        if let transferNote = note.trimmedNonEmpty {
            payload["note"] = transferNote
        }
        if !currency.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            payload["currency"] = currency.uppercased()
        }
        return try await submitTransfer(payload: payload, defaultMessage: "Transfer submitted.")
    }

    func sendToBeneficiary(
        beneficiary: BeneficiaryRecord,
        amount: Double,
        currency: String,
        note: String
    ) async throws -> String {
        let beneficiaryPayload = try buildBeneficiaryPayload(from: beneficiary)
        let verificationMap = try await FunctionsService.shared.callMap(
            function: .createBeneficiaryVerification,
            data: [
                "recipientBeneficiary": beneficiaryPayload,
                "amount": amount,
                "currency": currency.uppercased()
            ]
        )
        let verificationData = (verificationMap["data"] as? [String: Any]) ?? verificationMap
        let canProceed = verificationData.bool(keys: ["canProceed", "verified"], fallback: true)
        if !canProceed {
            throw WalletTransferError.verificationFailed(
                reason: verificationData.string(keys: ["reasonMessage", "message"]) ?? "Beneficiary verification failed."
            )
        }
        guard let verificationId = verificationData.string(keys: ["verificationId"]), !verificationId.isEmpty else {
            throw WalletTransferError.verificationFailed(reason: "Verification ID was not returned.")
        }

        var payload: [String: Any] = [
            "recipientBeneficiary": beneficiaryPayload,
            "beneficiaryVerificationId": verificationId,
            "amount": amount,
            "fundingSourceType": "MOBILE_MONEY"
        ]
        if let transferNote = note.trimmedNonEmpty {
            payload["note"] = transferNote
        }
        if !currency.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            payload["currency"] = currency.uppercased()
        }
        return try await submitTransfer(payload: payload, defaultMessage: "Transfer submitted.")
    }

    func sendToPaymentMethod(
        senderUserId: String,
        paymentMethod: PaymentMethodRecord,
        amount: Double,
        currency: String,
        note: String
    ) async throws -> String {
        let paymentMethodId = paymentMethod.id?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !paymentMethodId.isEmpty else {
            throw WalletTransferError.invalidPaymentMethod
        }
        let recipientId = senderUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !recipientId.isEmpty else {
            throw WalletTransferError.invalidRecipient
        }
        guard let destinationType = payoutDestinationType(for: paymentMethod) else {
            throw WalletTransferError.unsupportedPayoutMethod
        }

        var payload: [String: Any] = [
            "recipientId": recipientId,
            "recipientPaymentMethodId": paymentMethodId,
            "amount": amount,
            "fundingSourceType": "WALLET",
            "destinationType": destinationType
        ]
        if let transferNote = note.trimmedNonEmpty {
            payload["note"] = transferNote
        }
        if !currency.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            payload["currency"] = currency.uppercased()
        }
        return try await submitTransfer(payload: payload, defaultMessage: "Payout submitted.")
    }

    private func submitTransfer(payload: [String: Any], defaultMessage: String) async throws -> String {
        let map = try await FunctionsService.shared.callMap(
            function: .initiateTransfer,
            data: payload
        )
        let data = (map["data"] as? [String: Any]) ?? map
        let success = data.bool(keys: ["success"], fallback: true)
        if !success {
            let message = data.string(keys: ["message", "error"]) ?? "Transfer could not be completed."
            throw WalletTransferError.transferFailed(reason: message)
        }
        return data.string(keys: ["message"]) ?? map.string(keys: ["message"]) ?? defaultMessage
    }

    private func parseTransaction(_ doc: QueryDocumentSnapshot) -> WalletTransactionRecord {
        let data = doc.data()
        let amount = data.double(keys: ["amount", "transactionAmount", "value", "netAmount"]) ?? 0
        let status = data.string(keys: ["status", "state"]) ?? "unknown"
        let type = data.string(keys: ["type", "transactionType", "source"]) ?? "transaction"
        let note = data.string(keys: ["description", "note", "memo", "message", "reason"])
        let title = data.string(keys: ["title", "label", "name"]) ?? type
        let createdAt = data.date(keys: ["timestamp", "createdAt", "lastUpdatedAt", "processedAt", "updatedAt"])

        return WalletTransactionRecord(
            id: doc.documentID,
            title: title,
            type: type,
            amount: amount,
            status: status,
            createdAt: createdAt,
            note: note
        )
    }

    private func parseBeneficiary(_ doc: QueryDocumentSnapshot) -> BeneficiaryRecord? {
        if var decoded = try? doc.data(as: BeneficiaryRecord.self) {
            if decoded.id == nil || decoded.id?.isEmpty == true {
                decoded.id = doc.documentID
            }
            return decoded
        }

        let data = doc.data()
        return BeneficiaryRecord(
            id: doc.documentID,
            name: data.string(keys: ["name", "fullName", "recipientName"]),
            country: data.string(keys: ["country", "countryCode"]),
            network: data.string(keys: ["network", "provider"]),
            phone: data.string(keys: ["phone", "mobileNumber", "accountNumber"]),
            accountLast4: data.string(keys: ["accountLast4", "last4"]),
            type: data.string(keys: ["type"]),
            verificationStatus: data.string(keys: ["verificationStatus", "status"])
        )
    }

    private func parsePaymentMethod(_ doc: QueryDocumentSnapshot) -> PaymentMethodRecord? {
        if var decoded = try? doc.data(as: PaymentMethodRecord.self) {
            if decoded.id == nil || decoded.id?.isEmpty == true {
                decoded.id = doc.documentID
            }
            return decoded
        }

        let data = doc.data()
        return PaymentMethodRecord(
            id: doc.documentID,
            type: data.string(keys: ["type", "methodType"]),
            brand: data.string(keys: ["brand", "network", "provider"]),
            last4: data.string(keys: ["last4", "accountLast4", "maskedLast4"]),
            holderName: data.string(keys: ["holderName", "accountHolderName", "name"]),
            status: data.string(keys: ["status"])
        )
    }

    private func buildBeneficiaryPayload(from beneficiary: BeneficiaryRecord) throws -> [String: Any] {
        guard let name = beneficiary.name?.trimmedNonEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary name is missing.")
        }
        guard let country = beneficiary.country?.trimmedNonEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary country is missing.")
        }
        guard let phone = beneficiary.phone?.trimmedNonEmpty ?? beneficiary.accountLast4?.trimmedNonEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary account number or phone is missing.")
        }

        var payload: [String: Any] = [
            "name": name,
            "country": country,
            "accountNumber": phone,
            "mobileNumber": phone
        ]
        if let id = beneficiary.id?.trimmedNonEmpty { payload["id"] = id }
        if let network = beneficiary.network?.trimmedNonEmpty { payload["network"] = network }
        return payload
    }

    private func payoutDestinationType(for method: PaymentMethodRecord) -> String? {
        let normalizedType = (method.type ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let normalizedBrand = (method.brand ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if normalizedType.contains("CARD") || normalizedBrand.contains("VISA") || normalizedBrand.contains("MASTERCARD") || normalizedBrand.contains("AMEX") {
            return "CARD"
        }
        if normalizedType.contains("BANK") || normalizedType.contains("ACCOUNT") {
            return "BANK"
        }
        return nil
    }
}

private extension Dictionary where Key == String, Value == Any {
    func string(keys: [String]) -> String? {
        for key in keys {
            if let value = self[key] as? String {
                let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cleaned.isEmpty {
                    return cleaned
                }
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

    func bool(keys: [String], fallback: Bool = false) -> Bool {
        for key in keys {
            if let value = self[key] as? Bool {
                return value
            }
            if let number = self[key] as? NSNumber {
                return number.intValue != 0
            }
            if let text = self[key] as? String {
                let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                if ["true", "yes", "1"].contains(normalized) {
                    return true
                }
                if ["false", "no", "0"].contains(normalized) {
                    return false
                }
            }
        }
        return fallback
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

private extension Optional where Wrapped == String {
    var nonEmpty: String? {
        guard let self, !self.isEmpty else { return nil }
        return self
    }
}

private extension String {
    var trimmedNonEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

enum WalletTransferError: LocalizedError {
    case invalidRecipient
    case invalidBeneficiary(reason: String)
    case verificationFailed(reason: String)
    case invalidPaymentMethod
    case unsupportedPayoutMethod
    case transferFailed(reason: String)

    var errorDescription: String? {
        switch self {
        case .invalidRecipient:
            return "Recipient is required."
        case .invalidBeneficiary(let reason):
            return reason
        case .verificationFailed(let reason):
            return reason
        case .invalidPaymentMethod:
            return "Payment method is missing."
        case .unsupportedPayoutMethod:
            return "Selected method is not a bank account or card."
        case .transferFailed(let reason):
            return reason
        }
    }
}
