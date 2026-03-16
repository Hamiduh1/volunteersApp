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
