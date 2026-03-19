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

    func fetchPendingDepositCount(uid: String, limit: Int = 80) async throws -> Int {
        let collection = db.collection(FirestoreCollection.depositRequests.rawValue)
        let queries: [Query] = [
            collection.whereField("userId", isEqualTo: uid).limit(to: limit),
            collection.whereField("senderId", isEqualTo: uid).limit(to: limit)
        ]

        var docsByPath: [String: QueryDocumentSnapshot] = [:]
        for query in queries {
            do {
                let snapshot = try await query.getDocuments()
                for doc in snapshot.documents {
                    docsByPath[doc.reference.path] = doc
                }
            } catch {
                continue
            }
        }

        let pendingStatuses: Set<String> = ["PENDING", "PROCESSING", "QUEUED"]
        return docsByPath.values.filter { doc in
            let status = (doc.data().string(keys: ["status"]) ?? "").uppercased()
            return pendingStatuses.contains(status)
        }.count
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

    func addBeneficiary(
        uid: String,
        name: String,
        country: String,
        network: String,
        phone: String
    ) async throws -> BeneficiaryRecord {
        let cleanUid = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCountry = country.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNetwork = network.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanUid.isEmpty else {
            throw WalletTransferError.transferFailed(reason: "You must be logged in.")
        }
        guard !cleanName.isEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary name is required.")
        }
        guard !cleanCountry.isEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary country is required.")
        }
        guard !cleanNetwork.isEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Network is required.")
        }
        guard !cleanPhone.isEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Phone number is required.")
        }

        let ref = db.collection(FirestoreCollection.users.rawValue)
            .document(cleanUid)
            .collection(FirestoreSubcollection.beneficiaries.rawValue)
            .document()

        let payload: [String: Any] = [
            "name": cleanName,
            "country": cleanCountry,
            "network": cleanNetwork,
            "phone": cleanPhone,
            "accountLast4": String(cleanPhone.suffix(4)),
            "type": "MOBILE_MONEY",
            "verificationStatus": "UNVERIFIED",
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ]

        try await ref.setData(payload)

        return BeneficiaryRecord(
            id: ref.documentID,
            name: cleanName,
            country: cleanCountry,
            network: cleanNetwork,
            phone: cleanPhone,
            accountLast4: String(cleanPhone.suffix(4)),
            type: "MOBILE_MONEY",
            verificationStatus: "UNVERIFIED"
        )
    }

    func deleteBeneficiary(uid: String, beneficiaryId: String) async throws {
        let cleanUid = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanBeneficiaryId = beneficiaryId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanUid.isEmpty, !cleanBeneficiaryId.isEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary identifier is required.")
        }

        try await db.collection(FirestoreCollection.users.rawValue)
            .document(cleanUid)
            .collection(FirestoreSubcollection.beneficiaries.rawValue)
            .document(cleanBeneficiaryId)
            .delete()
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

    func fetchRecipientHasPayoutAccount(recipientUserId: String) async throws -> Bool {
        let recipientId = recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !recipientId.isEmpty else { return false }

        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(recipientId)
            .getDocument()
        let data = snapshot.data() ?? [:]
        let payoutAccount = data.string(keys: ["payoutAccountId", "stripeAccountId"])
        return payoutAccount?.isEmpty == false
    }

    func fetchRecipientPaymentMethods(recipientUserId: String, limit: Int = 60) async throws -> [PaymentMethodRecord] {
        let recipientId = recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !recipientId.isEmpty else { return [] }
        return try await fetchPaymentMethods(uid: recipientId, limit: limit)
    }

    func authorizeAgent() async throws -> String {
        let map = try await FunctionsService.shared.callMap(function: .payForAgentRole)
        let data = (map["data"] as? [String: Any]) ?? map
        return data.string(keys: ["message"]) ?? map.string(keys: ["message"]) ?? "Success! You are now an agent."
    }

    func completeAgentCashOut(secretCode: String) async throws -> String {
        let cleanCode = secretCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanCode.isEmpty else {
            throw WalletTransferError.transferFailed(reason: "Secret code is required.")
        }
        let map = try await FunctionsService.shared.callMap(
            function: .processAgentPayout,
            data: ["secretCode": cleanCode]
        )
        let data = (map["data"] as? [String: Any]) ?? map
        let success = data.bool(keys: ["success"], fallback: true)
        let message = data.string(keys: ["message", "error"]) ?? "Operation finished."
        if !success {
            throw WalletTransferError.transferFailed(reason: message)
        }
        return message
    }

    func cashOutAgentEarnings() async throws -> String {
        let map = try await FunctionsService.shared.callMap(function: .cashOutAgentEarnings)
        let data = (map["data"] as? [String: Any]) ?? map
        return data.string(keys: ["message"]) ?? map.string(keys: ["message"]) ?? "Earnings cash-out completed."
    }

    func calculateAgentCashOutFee(amount: Double) -> AgentCashOutFeeRecord {
        let rate: Double
        switch amount {
        case ..<1: rate = 0.09
        case ..<2: rate = 0.09
        case ..<5: rate = 0.05
        case ..<20: rate = 0.026
        case ..<40: rate = 0.0164
        case ..<200: rate = 0.013
        case ..<600: rate = 0.008
        case ..<1200: rate = 0.00475
        default: rate = 0.00314
        }
        let fee = ((amount * rate) * 100).rounded() / 100
        let totalDebit = ((amount + fee) * 100).rounded() / 100
        return AgentCashOutFeeRecord(rate: rate, fee: fee, totalDebit: totalDebit)
    }

    func generateAgentWithdrawalCode(
        uid: String,
        amount: Double,
        currency: String,
        currentBalance: Double
    ) async throws -> AgentWithdrawalCodeRecord {
        guard amount > 0 else {
            throw WalletTransferError.transferFailed(reason: "Amount must be positive.")
        }

        let fee = calculateAgentCashOutFee(amount: amount)
        guard currentBalance >= fee.totalDebit else {
            throw WalletTransferError.transferFailed(reason: "Insufficient funds (including fee).")
        }

        let code = String(Int.random(in: 100000...999999))
        let expiresAt = Date(timeIntervalSinceNow: 15 * 60)

        let requestData: [String: Any] = [
            "senderId": uid,
            "amount": amount,
            "currency": currency,
            "secretCode": code,
            "status": "PENDING",
            "createdAt": FieldValue.serverTimestamp(),
            "expiresAt": expiresAt
        ]

        _ = try await db.collection(FirestoreCollection.payoutRequests.rawValue).addDocument(data: requestData)
        return AgentWithdrawalCodeRecord(code: code, expiresAt: expiresAt, fee: fee)
    }

    func depositWithMobileMoney(
        uid: String,
        amount: Double,
        currency: String,
        phone: String,
        network: String,
        country: String,
        dialCode: String,
        localCurrency: String,
        paymentMethodId: String?,
        localAmount: Double?
    ) async throws -> String {
        guard amount > 0 else {
            throw WalletTransferError.transferFailed(reason: "Amount must be positive.")
        }

        let requestData: [String: Any] = [
            "senderId": uid,
            "amount": amount,
            "currency": currency,
            "phone": phone,
            "network": network,
            "country": country,
            "dialCode": dialCode,
            "localCurrency": localCurrency,
            "localAmount": localAmount as Any,
            "paymentMethodId": paymentMethodId ?? "",
            "type": "CASH_IN",
            "status": "PENDING",
            "timestamp": FieldValue.serverTimestamp()
        ]

        _ = try await db.collection(FirestoreCollection.payoutRequests.rawValue).addDocument(data: requestData)
        return "Deposit request sent. Approve on your phone. Wallet credit posts in \(currency) after provider confirmation."
    }

    func withdrawToMobileMoney(
        uid: String,
        amount: Double,
        currency: String,
        phone: String,
        network: String,
        country: String,
        dialCode: String,
        localCurrency: String,
        paymentMethodId: String?,
        localAmount: Double?
    ) async throws -> String {
        guard amount > 0 else {
            throw WalletTransferError.transferFailed(reason: "Amount must be positive.")
        }

        let userRef = db.collection(FirestoreCollection.users.rawValue).document(uid)
        let requestRef = db.collection(FirestoreCollection.payoutRequests.rawValue).document()

        _ = try await db.runTransaction { transaction, errorPointer in
            do {
                let snapshot = try transaction.getDocument(userRef)
                let data = snapshot.data() ?? [:]
                let wallet = data["wallet"] as? [String: Any] ?? [:]
                let balance = wallet.double(keys: ["balance", "availableBalance", "currentBalance"])
                    ?? data.double(keys: ["walletBalance", "balance"])
                    ?? 0.0
                if balance < amount {
                    throw WalletTransferError.transferFailed(reason: "Insufficient balance.")
                }

                transaction.updateData(
                    ["wallet.balance": FieldValue.increment(-amount)],
                    forDocument: userRef
                )

                let requestData: [String: Any] = [
                    "senderId": uid,
                    "amount": amount,
                    "currency": currency,
                    "phone": phone,
                    "network": network,
                    "country": country,
                    "dialCode": dialCode,
                    "localCurrency": localCurrency,
                    "localAmount": localAmount as Any,
                    "paymentMethodId": paymentMethodId ?? "",
                    "type": "CASH_OUT",
                    "status": "PENDING",
                    "timestamp": FieldValue.serverTimestamp()
                ]
                transaction.setData(requestData, forDocument: requestRef)
            } catch {
                errorPointer?.pointee = error as NSError
            }
            return nil
        }

        return "Withdrawal to your mobile money has been initiated."
    }

    func depositFromExternalSource(
        uid: String,
        amount: Double,
        paymentMethodId: String,
        walletCurrency: String
    ) async throws -> String {
        guard amount > 0 else {
            throw WalletTransferError.transferFailed(reason: "Deposit amount must be positive.")
        }

        let methodRef = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.paymentMethods.rawValue)
            .document(paymentMethodId)
        let methodSnap = try await methodRef.getDocument()
        guard methodSnap.exists else {
            throw WalletTransferError.transferFailed(reason: "Selected payment method was not found.")
        }

        let methodData = methodSnap.data() ?? [:]
        let methodType = (methodData.string(keys: ["type", "methodType"]) ?? "").uppercased()
        let isCard = methodType == "CARD"
        let isAchEnabledBank = methodType == "BANK" &&
            (methodData.bool(keys: ["achDebitEnabled"], fallback: false) ||
                (methodData.string(keys: ["chargeSourceId"])?.isEmpty == false))

        if !isCard && !isAchEnabledBank {
            throw WalletTransferError.transferFailed(
                reason: "This funding source is not enabled for deposits. Use a linked card or ACH-enabled bank account."
            )
        }
        if isAchEnabledBank && !walletCurrency.uppercased().elementsEqual("USD") {
            throw WalletTransferError.transferFailed(reason: "ACH deposits are currently available for USD wallets only.")
        }

        let depositRequest: [String: Any] = [
            "userId": uid,
            "amount": amount,
            "currency": walletCurrency,
            "paymentMethodId": paymentMethodId,
            "status": "PENDING",
            "type": "DEPOSIT",
            "createdAt": FieldValue.serverTimestamp()
        ]
        _ = try await db.collection(FirestoreCollection.depositRequests.rawValue).addDocument(data: depositRequest)

        if isCard {
            return "Deposit submitted from your card. It should reflect shortly."
        }
        return "ACH deposit initiated from your bank account. Settlement is pending and may take 1-3 business days."
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
        try await sendToRecipientPayout(
            recipientUserId: senderUserId,
            paymentMethod: paymentMethod,
            destinationType: nil,
            amount: amount,
            currency: currency,
            note: note
        )
    }

    func sendToRecipientPayout(
        recipientUserId: String,
        paymentMethod: PaymentMethodRecord,
        destinationType: String?,
        amount: Double,
        currency: String,
        note: String
    ) async throws -> String {
        let recipientId = recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !recipientId.isEmpty else {
            throw WalletTransferError.invalidRecipient
        }

        let methodId = paymentMethod.id?.trimmingCharacters(in: .whitespacesAndNewlines)
        let externalAccountId = paymentMethod.externalAccountId?.trimmingCharacters(in: .whitespacesAndNewlines)
        let recipientMethodIdentifier = methodId?.isEmpty == false ? methodId! : (externalAccountId ?? "")
        guard !recipientMethodIdentifier.isEmpty else {
            throw WalletTransferError.invalidPaymentMethod
        }

        let resolvedDestinationType: String
        if let explicit = destinationType?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
           !explicit.isEmpty {
            resolvedDestinationType = explicit
        } else if let inferred = payoutDestinationType(for: paymentMethod) {
            resolvedDestinationType = inferred
        } else {
            throw WalletTransferError.unsupportedPayoutMethod
        }

        var payload: [String: Any] = [
            "recipientId": recipientId,
            "recipientPaymentMethodId": recipientMethodIdentifier,
            "amount": amount,
            "fundingSourceType": "WALLET",
            "destinationType": resolvedDestinationType
        ]
        if let externalAccountId, !externalAccountId.isEmpty {
            payload["recipientExternalAccountId"] = externalAccountId
        }
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
        let source = data.string(keys: ["source", "fundingSourceType", "destinationType"])
        let note = data.string(keys: ["description", "note", "memo", "message", "reason"])
        let title = data.string(keys: ["title", "label", "name"]) ?? type
        let createdAt = data.date(keys: ["timestamp", "createdAt", "lastUpdatedAt", "processedAt", "updatedAt"])

        return WalletTransactionRecord(
            id: doc.documentID,
            title: title,
            type: type,
            amount: amount,
            status: status,
            source: source,
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
            label: data.string(keys: ["label"]),
            isDefault: data.bool(keys: ["isDefault"], fallback: false),
            brand: data.string(keys: ["brand", "network", "provider"]),
            last4: data.string(keys: ["last4", "accountLast4", "maskedLast4"]),
            holderName: data.string(keys: ["holderName", "accountHolderName", "name"]),
            cardHolderName: data.string(keys: ["cardHolderName", "holderName"]),
            cardNumber: data.string(keys: ["cardNumber"]),
            expiryDate: data.string(keys: ["expiryDate"]),
            status: data.string(keys: ["status"]),
            accountHolderName: data.string(keys: ["accountHolderName"]),
            bankName: data.string(keys: ["bankName", "bank", "institutionName"]),
            accountNumber: data.string(keys: ["accountNumber"]),
            routingNumber: data.string(keys: ["routingNumber"]),
            network: data.string(keys: ["network", "provider"]),
            country: data.string(keys: ["country", "countryCode"]),
            dialCode: data.string(keys: ["dialCode"]),
            currency: data.string(keys: ["currency"]),
            registeredName: data.string(keys: ["registeredName"]),
            phoneNumber: data.string(keys: ["phoneNumber", "phone", "mobileNumber"]),
            verificationStatus: data.string(keys: ["verificationStatus"]),
            verificationMethod: data.string(keys: ["verificationMethod"]),
            lastVerificationError: data.string(keys: ["lastVerificationError"]),
            externalAccountId: data.string(keys: ["externalAccountId", "stripeExternalAccountId"]),
            chargePaymentMethodId: data.string(keys: ["chargePaymentMethodId"]),
            stripePaymentMethodId: data.string(keys: ["stripePaymentMethodId"]),
            chargeSourceId: data.string(keys: ["chargeSourceId"]),
            chargeCustomerId: data.string(keys: ["chargeCustomerId"]),
            chargeSourceStatus: data.string(keys: ["chargeSourceStatus"]),
            achDebitEnabled: data.bool(keys: ["achDebitEnabled"], fallback: false),
            achCreditEnabled: data.bool(keys: ["achCreditEnabled"], fallback: false),
            requiresRelinkForCharges: data.bool(keys: ["requiresRelinkForCharges"], fallback: false),
            phoneOwnershipVerified: data.bool(keys: ["phoneOwnershipVerified", "isPhoneVerified"], fallback: false)
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
