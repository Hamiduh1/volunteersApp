import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class GlobalWalletRepository {
    private let db = Firestore.firestore()

    private func afriexCountryCode(for country: String) -> String? {
        let normalized = country.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let codes = [
            "benin": "BJ", "botswana": "BW", "cameroon": "CM", "congo (brazzaville)": "CG",
            "cote d'ivoire": "CI", "cote d’ivoire": "CI", "egypt": "EG", "ethiopia": "ET",
            "gambia": "GM", "ghana": "GH", "guinea": "GN", "guinea (conakry)": "GN",
            "kenya": "KE", "madagascar": "MG", "malawi": "MW", "mozambique": "MZ",
            "nigeria": "NG", "rwanda": "RW", "senegal": "SN", "sierra leone": "SL",
            "south africa": "ZA", "tanzania": "TZ", "uganda": "UG", "zambia": "ZM"
        ]
        return codes[normalized]
    }

    private func afriexDialCode(for country: String) -> String? {
        let normalized = country.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let codes = [
            "benin": "229", "botswana": "267", "cameroon": "237", "congo (brazzaville)": "242",
            "cote d'ivoire": "225", "cote dâ€™ivoire": "225", "ethiopia": "251", "gambia": "220",
            "ghana": "233", "guinea": "224", "guinea (conakry)": "224", "kenya": "254",
            "madagascar": "261", "malawi": "265", "mozambique": "258", "nigeria": "234",
            "rwanda": "250", "senegal": "221", "sierra leone": "232", "tanzania": "255",
            "uganda": "256", "zambia": "260"
        ]
        return codes[normalized]
    }

    /// Converts an iOS local-number entry to the E.164 form Afriex expects.
    private func normalizedAfriexMobileMoneyPhone(_ rawPhone: String, country: String) -> String? {
        let trimmed = rawPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        let digits = trimmed.filter(\.isNumber)
        guard !digits.isEmpty else { return nil }
        if trimmed.hasPrefix("+") {
            return (7...15).contains(digits.count) ? "+\(digits)" : nil
        }
        if digits.hasPrefix("00") {
            let e164Digits = String(digits.dropFirst(2))
            return (7...15).contains(e164Digits.count) ? "+\(e164Digits)" : nil
        }
        guard let dialCode = afriexDialCode(for: country) else { return nil }
        if digits.hasPrefix(dialCode) {
            return (7...15).contains(digits.count) ? "+\(digits)" : nil
        }
        let localNumber = digits.hasPrefix("0") ? String(digits.dropFirst()) : digits
        let e164Digits = dialCode + localNumber
        return (7...15).contains(e164Digits.count) ? "+\(e164Digits)" : nil
    }

    private func normalizedAfriexToken(_ value: String) -> String {
        value.uppercased().filter { $0.isLetter || $0.isNumber }
    }

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

        guard let phoneE164 = normalizedAfriexMobileMoneyPhone(cleanPhone, country: cleanCountry) else {
            throw WalletTransferError.invalidBeneficiary(reason: "Enter a valid mobile number for the selected country.")
        }

        guard let countryCode = afriexCountryCode(for: cleanCountry) else {
            throw WalletTransferError.invalidBeneficiary(reason: "Choose an Afriex-supported mobile money country.")
        }

        let institutionsMap = try await FunctionsService.shared.callMap(
            function: .getAfriexInstitutions,
            data: ["channel": "MOBILE_MONEY", "countryCode": countryCode]
        )
        let institutions = institutionsMap["institutions"] as? [[String: Any]] ?? []
        let matches = institutions.filter { institution in
            guard let name = institution["institutionName"] as? String else { return false }
            return normalizedAfriexToken(name) == normalizedAfriexToken(cleanNetwork)
        }
        guard matches.count == 1, let institutionCode = matches[0]["institutionCode"] as? String,
              !institutionCode.isEmpty else {
            throw WalletTransferError.invalidBeneficiary(
                reason: "Choose the recipient's current mobile money provider from Afriex."
            )
        }

        let verified = try await FunctionsService.shared.callMap(
            function: .saveVerifiedBeneficiary,
            data: [
                "type": "MOBILE_MONEY",
                "country": cleanCountry,
                "name": cleanName,
                "phone": phoneE164,
                "network": cleanNetwork,
                "accountNumber": phoneE164,
                "institutionCode": institutionCode,
                "recipientDetailsConfirmed": true,
                "confirmedRecipientName": cleanName,
                "confirmedRecipientPhone": phoneE164,
                "confirmedInstitutionCode": institutionCode
            ]
        )
        guard let beneficiaryId = verified["beneficiaryId"] as? String, !beneficiaryId.isEmpty else {
            throw WalletTransferError.transferFailed(reason: "The verified beneficiary was saved without an identifier.")
        }

        let resolvedPhone = (verified["phone"] as? String) ?? phoneE164
        let resolvedAccountNumber = (verified["accountNumber"] as? String) ?? phoneE164
        return BeneficiaryRecord(
            id: beneficiaryId,
            name: (verified["recipientName"] as? String) ?? cleanName,
            country: cleanCountry,
            network: (verified["institutionName"] as? String) ?? cleanNetwork,
            phone: resolvedPhone,
            accountLast4: String(resolvedAccountNumber.suffix(4)),
            type: (verified["type"] as? String) ?? "MOBILE_MONEY",
            verificationStatus: verified["verificationStatus"] as? String,
            mobileNumber: (verified["mobileNumber"] as? String) ?? phoneE164,
            accountNumber: resolvedAccountNumber,
            institutionCode: verified["institutionCode"] as? String,
            bankName: verified["bankName"] as? String,
            swiftCode: verified["swiftCode"] as? String,
            routingCode: verified["routingCode"] as? String,
            recipientEmail: verified["recipientEmail"] as? String,
            recipientAddress: verified["recipientAddress"] as? String,
            bankAddress: verified["bankAddress"] as? String,
            invoiceReference: verified["invoiceReference"] as? String,
            providerResolvedName: verified["providerResolvedName"] as? String,
            accountNameVerified: verified["accountNameVerified"] as? Bool,
            accountRouteVerified: verified["accountRouteVerified"] as? Bool,
            recipientDetailsConfirmed: verified["recipientDetailsConfirmed"] as? Bool,
            recipientNameConfirmationSource: verified["recipientNameConfirmationSource"] as? String,
            recipientNameConfirmedAtMs: verified["recipientNameConfirmedAtMs"] as? Double,
            providerVerifiedAtMs: verified["providerVerifiedAtMs"] as? Double
        )
    }

    /// Revalidates a recipient created by an older client without trusting any
    /// legacy verification flag. The Function owns the saved destination data.
    func refreshSavedBeneficiaryVerification(beneficiaryId: String) async throws -> BeneficiaryRecord {
        let cleanId = beneficiaryId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Select a saved beneficiary first.")
        }
        let verified = try await FunctionsService.shared.callMap(
            function: .refreshSavedBeneficiaryVerification,
            data: ["beneficiaryId": cleanId, "recipientDetailsConfirmed": true]
        )
        guard let returnedId = verified["beneficiaryId"] as? String, !returnedId.isEmpty else {
            throw WalletTransferError.transferFailed(reason: "The refreshed beneficiary was returned without an identifier.")
        }
        let phone = (verified["phone"] as? String) ?? ""
        let accountNumber = verified["accountNumber"] as? String
        return BeneficiaryRecord(
            id: returnedId,
            name: verified["recipientName"] as? String,
            country: verified["country"] as? String,
            network: verified["institutionName"] as? String,
            phone: phone,
            accountLast4: String((accountNumber ?? phone).suffix(4)),
            type: verified["type"] as? String,
            verificationStatus: verified["verificationStatus"] as? String,
            mobileNumber: verified["mobileNumber"] as? String,
            accountNumber: accountNumber,
            institutionCode: verified["institutionCode"] as? String,
            bankName: verified["bankName"] as? String,
            swiftCode: verified["swiftCode"] as? String,
            routingCode: verified["routingCode"] as? String,
            recipientEmail: verified["recipientEmail"] as? String,
            recipientAddress: verified["recipientAddress"] as? String,
            bankAddress: verified["bankAddress"] as? String,
            invoiceReference: verified["invoiceReference"] as? String,
            providerResolvedName: verified["providerResolvedName"] as? String,
            accountNameVerified: verified["accountNameVerified"] as? Bool,
            accountRouteVerified: verified["accountRouteVerified"] as? Bool,
            recipientDetailsConfirmed: verified["recipientDetailsConfirmed"] as? Bool,
            recipientNameConfirmationSource: verified["recipientNameConfirmationSource"] as? String,
            recipientNameConfirmedAtMs: verified["recipientNameConfirmedAtMs"] as? Double,
            providerVerifiedAtMs: verified["providerVerifiedAtMs"] as? Double
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
        let response = try await FunctionsService.shared.callMap(
            function: .getRecipientPayoutMethods,
            data: ["recipientId": recipientId]
        )
        let data = (response["data"] as? [String: Any]) ?? response
        let methods = data["methods"] as? [[String: Any]] ?? []
        return methods
            .prefix(limit)
            .compactMap(parsePaymentMethod)
            .sorted { ($0.brand ?? $0.bankName ?? $0.type ?? "").localizedCaseInsensitiveCompare($1.brand ?? $1.bankName ?? $1.type ?? "") == .orderedAscending }
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
        guard beneficiaryDeliveryRoute(beneficiary) == "MOBILE_MONEY" else {
            throw WalletTransferError.invalidBeneficiary(
                reason: "This saved bank or SWIFT recipient can be refreshed on iOS, but it cannot be sent through the iOS mobile-money lane. Use the Bank Account lane until this iOS build includes it."
            )
        }
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
            verificationStatus: data.string(keys: ["verificationStatus", "status"]),
            mobileNumber: data.string(keys: ["mobileNumber"]),
            accountNumber: data.string(keys: ["accountNumber"]),
            institutionCode: data.string(keys: ["institutionCode"]),
            bankName: data.string(keys: ["bankName"]),
            swiftCode: data.string(keys: ["swiftCode"]),
            routingCode: data.string(keys: ["routingCode", "routingNumber"]),
            recipientEmail: data.string(keys: ["recipientEmail"]),
            recipientAddress: data.string(keys: ["recipientAddress"]),
            bankAddress: data.string(keys: ["bankAddress", "institutionAddress"]),
            invoiceReference: data.string(keys: ["invoiceReference"]),
            providerResolvedName: data.string(keys: ["providerResolvedName"]),
            accountNameVerified: data.bool(keys: ["accountNameVerified"], fallback: false),
            accountRouteVerified: data.bool(keys: ["accountRouteVerified"], fallback: false),
            recipientDetailsConfirmed: data.bool(keys: ["recipientDetailsConfirmed"], fallback: false),
            recipientNameConfirmationSource: data.string(keys: ["recipientNameConfirmationSource"]),
            recipientNameConfirmedAtMs: data.double(keys: ["recipientNameConfirmedAtMs"]),
            providerVerifiedAtMs: data.double(keys: ["providerVerifiedAtMs"])
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
            payoutReady: data.bool(keys: ["payoutReady", "externalAccountAttached"], fallback: false),
            requiresRelinkForCharges: data.bool(keys: ["requiresRelinkForCharges"], fallback: false),
            phoneOwnershipVerified: data.bool(keys: ["phoneOwnershipVerified", "isPhoneVerified"], fallback: false)
        )
    }

    private func parsePaymentMethod(_ data: [String: Any]) -> PaymentMethodRecord? {
        guard let id = data.string(keys: ["id"]), !id.isEmpty else { return nil }
        return PaymentMethodRecord(
            id: id,
            type: data.string(keys: ["type", "methodType"]),
            label: data.string(keys: ["label"]),
            isDefault: data.bool(keys: ["isDefault"], fallback: false),
            brand: data.string(keys: ["brand", "network", "provider"]),
            last4: data.string(keys: ["last4", "accountLast4", "maskedLast4"]),
            holderName: data.string(keys: ["holderName", "accountHolderName", "name"]),
            cardHolderName: data.string(keys: ["cardHolderName", "holderName"]),
            status: data.string(keys: ["status"]),
            accountHolderName: data.string(keys: ["accountHolderName"]),
            bankName: data.string(keys: ["bankName", "bank", "institutionName"]),
            network: data.string(keys: ["network", "provider"]),
            country: data.string(keys: ["country", "countryCode"]),
            currency: data.string(keys: ["currency"]),
            payoutReady: data.bool(keys: ["payoutReady", "externalAccountAttached"], fallback: false)
        )
    }

    private func buildBeneficiaryPayload(from beneficiary: BeneficiaryRecord) throws -> [String: Any] {
        guard let name = beneficiary.name?.trimmedNonEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary name is missing.")
        }
        guard let country = beneficiary.country?.trimmedNonEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary country is missing.")
        }
        guard let accountNumber = beneficiary.accountNumber?.trimmedNonEmpty ??
                beneficiary.mobileNumber?.trimmedNonEmpty ??
                beneficiary.phone?.trimmedNonEmpty else {
            throw WalletTransferError.invalidBeneficiary(reason: "Beneficiary account number or phone is missing.")
        }

        let route = beneficiaryDeliveryRoute(beneficiary)
        var payload: [String: Any] = [
            "name": name,
            "country": country,
            "accountNumber": accountNumber,
            "type": beneficiary.type?.trimmingCharacters(in: .whitespacesAndNewlines) ?? route
        ]
        if let id = beneficiary.id?.trimmedNonEmpty { payload["id"] = id }
        if let network = beneficiary.network?.trimmedNonEmpty { payload["network"] = network }
        if route == "MOBILE_MONEY" { payload["mobileNumber"] = accountNumber }
        if let code = beneficiary.institutionCode?.trimmedNonEmpty {
            payload["institutionCode"] = code
            payload["bankCode"] = code
        }
        if let bankName = beneficiary.bankName?.trimmedNonEmpty { payload["bankName"] = bankName }
        if let swiftCode = beneficiary.swiftCode?.trimmedNonEmpty { payload["swiftCode"] = swiftCode }
        if let routingCode = beneficiary.routingCode?.trimmedNonEmpty { payload["routingCode"] = routingCode }
        if let recipientEmail = beneficiary.recipientEmail?.trimmedNonEmpty { payload["recipientEmail"] = recipientEmail }
        if let recipientAddress = beneficiary.recipientAddress?.trimmedNonEmpty { payload["recipientAddress"] = recipientAddress }
        if let bankAddress = beneficiary.bankAddress?.trimmedNonEmpty { payload["bankAddress"] = bankAddress }
        if let invoiceReference = beneficiary.invoiceReference?.trimmedNonEmpty { payload["invoiceReference"] = invoiceReference }
        return payload
    }

    private func beneficiaryDeliveryRoute(_ beneficiary: BeneficiaryRecord) -> String {
        let type = (beneficiary.type ?? "MOBILE_MONEY")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
        if type.contains("SWIFT") { return "SWIFT" }
        if type.contains("BANK") { return "BANK" }
        return "MOBILE_MONEY"
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
