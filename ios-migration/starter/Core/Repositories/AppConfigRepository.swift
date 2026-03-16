import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class AppConfigRepository {
    private let db = Firestore.firestore()

    func fetchPrivacyPolicy() async throws -> AppConfigTextRecord {
        try await fetchAppConfigText(documentId: "privacy_policy")
    }

    func fetchTermsAndConditions() async throws -> AppConfigTextRecord {
        try await fetchAppConfigText(documentId: "terms_and_conditions")
    }

    func fetchFaq(limit: Int = 50) async throws -> [FAQItemRecord] {
        let snapshot = try await db.collection(FirestoreCollection.howToUseTips.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: FAQItemRecord.self)
        }
    }

    func fetchGeneralSupportItems(limit: Int = 50) async throws -> [SupportItemRecord] {
        let snapshot = try await db.collection(FirestoreCollection.generalSupportItems.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: SupportItemRecord.self)
        }
    }

    private func fetchAppConfigText(documentId: String) async throws -> AppConfigTextRecord {
        let snapshot = try await db.collection(FirestoreCollection.appConfig.rawValue)
            .document(documentId)
            .getDocument()

        if let record = try? snapshot.data(as: AppConfigTextRecord.self) {
            return record
        }

        let data = snapshot.data() ?? [:]
        return AppConfigTextRecord(
            title: (data["title"] as? String) ?? (data["name"] as? String),
            content: (data["content"] as? String)
                ?? (data["text"] as? String)
                ?? (data["body"] as? String)
                ?? (data["markdown"] as? String),
            updatedAt: (data["updatedAt"] as? Timestamp) ?? (data["lastUpdatedAt"] as? Timestamp)
        )
    }
}
