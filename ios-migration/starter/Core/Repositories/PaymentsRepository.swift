import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class PaymentsRepository {
    private let db = Firestore.firestore()

    func fetchPaymentMethods(uid: String, limit: Int = 50) async throws -> [PaymentMethodRecord] {
        let collection = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.paymentMethods.rawValue)

        let queries: [Query] = [
            collection.order(by: "createdAt", descending: true).limit(to: limit),
            collection.order(by: "timestamp", descending: true).limit(to: limit),
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
                if !docsByPath.isEmpty { break }
            } catch {
                lastError = error
            }
        }

        if docsByPath.isEmpty, let lastError {
            throw lastError
        }

        return docsByPath.values
            .compactMap(parsePaymentMethod)
            .sorted { ($0.brand ?? $0.type ?? "").localizedCaseInsensitiveCompare($1.brand ?? $1.type ?? "") == .orderedAscending }
    }

    func fetchPayoutStatus() async throws -> PayoutSetupStatusRecord {
        let map = try await FunctionsService.shared.callMap(function: .getConnectAccountStatus)
        let data = (map["data"] as? [String: Any]) ?? map

        return PayoutSetupStatusRecord(
            hasAccount: data.bool(keys: ["hasAccount", "hasStripeAccount", "accountExists"], fallback: false),
            detailsSubmitted: data.bool(keys: ["detailsSubmitted", "isDetailsSubmitted"], fallback: false),
            payoutsEnabled: data.bool(keys: ["payoutsEnabled", "isPayoutsEnabled"], fallback: false),
            chargesEnabled: data.bool(keys: ["chargesEnabled", "isChargesEnabled"], fallback: false)
        )
    }

    func createConnectAccount() async throws {
        _ = try await FunctionsService.shared.call(function: .createConnectAccount)
    }

    func createOnboardingLink() async throws -> String {
        let map = try await FunctionsService.shared.callMap(function: .createConnectOnboardingLink)
        let data = (map["data"] as? [String: Any]) ?? map
        return data.string(keys: ["url", "onboardingUrl", "accountLinkUrl", "link"]) ?? ""
    }

    func addCard(
        cardHolderName: String,
        cardNumber: String,
        expiryDate: String,
        isDefault: Bool
    ) async throws -> (paymentMethodId: String, message: String) {
        let digits = cardNumber.filter(\.isNumber)
        let last4 = String(digits.suffix(4))
        let payload: [String: Any] = [
            "type": "CARD",
            "label": "Card ending in \(last4)",
            "cardHolderName": cardHolderName,
            "cardNumber": "**** **** **** \(last4)",
            "expiryDate": expiryDate,
            "brand": "VISA",
            "last4": last4,
            "isDefault": isDefault
        ]
        return try await addPaymentMethod(payload: payload)
    }

    func addBankAccount(
        bankName: String,
        accountHolderName: String,
        accountNumber: String,
        isDefault: Bool
    ) async throws -> (paymentMethodId: String, message: String) {
        let digits = accountNumber.filter(\.isNumber)
        let last4 = String(digits.suffix(4))
        let payload: [String: Any] = [
            "type": "BANK",
            "label": bankName,
            "bankName": bankName,
            "accountHolderName": accountHolderName,
            "accountNumber": "********\(last4)",
            "last4": last4,
            "isDefault": isDefault
        ]
        return try await addPaymentMethod(payload: payload)
    }

    func addMobileMoneyAccount(
        phone: String,
        network: String,
        registeredName: String,
        country: String,
        dialCode: String,
        currency: String,
        isDefault: Bool
    ) async throws -> (paymentMethodId: String, message: String) {
        let payload: [String: Any] = [
            "type": "MOBILE_MONEY",
            "label": "\(network) (\(phone))",
            "phoneNumber": phone,
            "network": network,
            "registeredName": registeredName,
            "country": country,
            "dialCode": dialCode,
            "currency": currency,
            "isDefault": isDefault
        ]
        return try await addPaymentMethod(payload: payload)
    }

    func requestMobileMoneyVerification(
        paymentMethodId: String,
        amountUsd: Double? = nil
    ) async throws -> String {
        guard !paymentMethodId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw PaymentMethodsError.invalidInput("Payment method id is required.")
        }
        var payload: [String: Any] = ["paymentMethodId": paymentMethodId]
        if let amountUsd, amountUsd > 0 {
            payload["amountUsd"] = amountUsd
        }
        let map = try await FunctionsService.shared.callMap(
            function: .requestMobileMoneyMethodVerification,
            data: payload
        )
        let data = (map["data"] as? [String: Any]) ?? map
        return data.string(keys: ["message"]) ?? "Verification request sent. Approve on your phone."
    }

    func deletePaymentMethod(uid: String, methodId: String) async throws {
        let cleanUid = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanMethodId = methodId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanUid.isEmpty, !cleanMethodId.isEmpty else {
            throw PaymentMethodsError.invalidInput("Payment method identifier is required.")
        }
        try await db.collection(FirestoreCollection.users.rawValue)
            .document(cleanUid)
            .collection(FirestoreSubcollection.paymentMethods.rawValue)
            .document(cleanMethodId)
            .delete()
    }

    func setDefaultPaymentMethod(uid: String, methodId: String) async throws {
        let cleanUid = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanMethodId = methodId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanUid.isEmpty, !cleanMethodId.isEmpty else {
            throw PaymentMethodsError.invalidInput("Payment method identifier is required.")
        }
        let collection = db.collection(FirestoreCollection.users.rawValue)
            .document(cleanUid)
            .collection(FirestoreSubcollection.paymentMethods.rawValue)
        let snapshot = try await collection.getDocuments()
        let batch = db.batch()
        for doc in snapshot.documents {
            batch.updateData(["isDefault": doc.documentID == cleanMethodId], forDocument: doc.reference)
        }
        try await batch.commit()
    }

    private func addPaymentMethod(payload: [String: Any]) async throws -> (paymentMethodId: String, message: String) {
        let map = try await FunctionsService.shared.callMap(function: .addPaymentMethod, data: payload)
        let data = (map["data"] as? [String: Any]) ?? map
        guard let paymentMethodId = data.string(keys: ["paymentMethodId", "id"]), !paymentMethodId.isEmpty else {
            throw PaymentMethodsError.invalidResponse("Failed to save payment method.")
        }
        let message = data.string(keys: ["message"]) ?? "Payment method saved successfully."
        return (paymentMethodId, message)
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
            last4: data.string(keys: ["last4", "accountLast4", "maskedLast4"]) ?? extractLast4(from: data.string(keys: ["cardNumber", "accountNumber"])),
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

    private func extractLast4(from maskedValue: String?) -> String? {
        guard let maskedValue else { return nil }
        let digits = maskedValue.filter(\.isNumber)
        guard !digits.isEmpty else { return nil }
        return String(digits.suffix(4))
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
}

enum PaymentMethodsError: LocalizedError {
    case invalidInput(String)
    case invalidResponse(String)

    var errorDescription: String? {
        switch self {
        case .invalidInput(let message):
            return message
        case .invalidResponse(let message):
            return message
        }
    }
}

