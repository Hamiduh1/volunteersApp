import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class PaymentsRepository {
    private let db = Firestore.firestore()

    func fetchPaymentMethods(uid: String, limit: Int = 50) async throws -> [PaymentMethodRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.paymentMethods.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: PaymentMethodRecord.self)
        }
    }

    func fetchPayoutStatus() async throws -> PayoutSetupStatusRecord {
        let map = try await FunctionsService.shared.callMap(function: .getConnectAccountStatus)
        let data = (map["data"] as? [String: Any]) ?? map

        return PayoutSetupStatusRecord(
            hasAccount: (data["hasAccount"] as? Bool) ?? ((data["hasStripeAccount"] as? Bool) ?? false),
            detailsSubmitted: (data["detailsSubmitted"] as? Bool) ?? false,
            payoutsEnabled: (data["payoutsEnabled"] as? Bool) ?? false,
            chargesEnabled: (data["chargesEnabled"] as? Bool) ?? false
        )
    }

    func createConnectAccount() async throws {
        _ = try await FunctionsService.shared.call(function: .createConnectAccount)
    }

    func createOnboardingLink() async throws -> String {
        let map = try await FunctionsService.shared.callMap(function: .createConnectOnboardingLink)
        let data = (map["data"] as? [String: Any]) ?? map
        return (data["url"] as? String)
            ?? (data["onboardingUrl"] as? String)
            ?? ""
    }
}
