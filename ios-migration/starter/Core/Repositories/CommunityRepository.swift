import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class CommunityRepository {
    private let db = Firestore.firestore()

    func fetchMindLoomPosts(limit: Int = 120) async throws -> [MindLoomPostRecord] {
        let snapshot = try await db.collection(FirestoreCollection.jokes.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: MindLoomPostRecord.self)
        }
        .sorted {
            let l = postDate($0)
            let r = postDate($1)
            return l > r
        }
    }

    func fetchMindLoomPosts(authorId: String, limit: Int = 120) async throws -> [MindLoomPostRecord] {
        let snapshot = try await db.collection(FirestoreCollection.jokes.rawValue)
            .whereField("authorId", isEqualTo: authorId)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: MindLoomPostRecord.self)
        }
        .sorted {
            let l = postDate($0)
            let r = postDate($1)
            return l > r
        }
    }

    func createMindLoomTextPost(user: AppSessionUser, text: String) async throws {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }

        let userDoc = try await db.collection(FirestoreCollection.users.rawValue).document(user.uid).getDocument()
        let data = userDoc.data() ?? [:]
        let name = (data["name"] as? String) ?? (data["username"] as? String) ?? (user.email ?? "User")
        let profileUrl = (data["profileImageUrl"] as? String) ?? (data["profilePictureUrl"] as? String)

        try await db.collection(FirestoreCollection.jokes.rawValue)
            .document()
            .setData([
                "authorId": user.uid,
                "authorName": name,
                "authorProfileUrl": profileUrl as Any,
                "text": clean,
                "mediaType": "TEXT",
                "status": "ACTIVE",
                "likes": [],
                "commentsCount": 0,
                "timestamp": FieldValue.serverTimestamp()
            ])
    }

    func toggleMindLoomLike(post: MindLoomPostRecord, uid: String) async throws {
        guard let postId = post.id, !postId.isEmpty else { return }
        let likes = post.likes ?? []
        let ref = db.collection(FirestoreCollection.jokes.rawValue).document(postId)
        if likes.contains(uid) {
            try await ref.updateData(["likes": FieldValue.arrayRemove([uid])])
        } else {
            try await ref.updateData(["likes": FieldValue.arrayUnion([uid])])
        }
    }

    func fetchMindLoomProfile(authorId: String) async throws -> MindLoomProfileSummary {
        let userDoc = try await db.collection(FirestoreCollection.users.rawValue).document(authorId).getDocument()
        let userData = userDoc.data() ?? [:]

        async let followersSnap = db.collection(FirestoreCollection.users.rawValue)
            .document(authorId)
            .collection(FirestoreSubcollection.followers.rawValue)
            .getDocuments()

        async let followingSnap = db.collection(FirestoreCollection.users.rawValue)
            .document(authorId)
            .collection(FirestoreSubcollection.following.rawValue)
            .getDocuments()

        async let posts = fetchMindLoomPosts(authorId: authorId, limit: 300)

        let followersSnapshot = try await followersSnap
        let followingSnapshot = try await followingSnap
        let authorPosts = try await posts

        let followers = followersSnapshot.documents.count
        let following = followingSnapshot.documents.count
        let likesCount = authorPosts.reduce(0) { $0 + (($1.likes ?? []).count) }

        return MindLoomProfileSummary(
            authorId: authorId,
            authorName: (userData["name"] as? String) ?? (userData["username"] as? String) ?? "User",
            authorEmail: (userData["email"] as? String) ?? "",
            authorProfileUrl: (userData["profileImageUrl"] as? String) ?? (userData["profilePictureUrl"] as? String),
            followersCount: followers,
            followingCount: following,
            likesCount: likesCount
        )
    }

    func setFollow(currentUid: String, targetUid: String, follow: Bool) async throws {
        guard currentUid != targetUid else { return }

        let targetFollowersRef = db.collection(FirestoreCollection.users.rawValue)
            .document(targetUid)
            .collection(FirestoreSubcollection.followers.rawValue)
            .document(currentUid)

        let currentFollowingRef = db.collection(FirestoreCollection.users.rawValue)
            .document(currentUid)
            .collection(FirestoreSubcollection.following.rawValue)
            .document(targetUid)

        if follow {
            try await targetFollowersRef.setData(["uid": currentUid, "timestamp": FieldValue.serverTimestamp()], merge: true)
            try await currentFollowingRef.setData(["uid": targetUid, "timestamp": FieldValue.serverTimestamp()], merge: true)
        } else {
            try? await targetFollowersRef.delete()
            try? await currentFollowingRef.delete()
        }
    }

    func isFollowing(currentUid: String, targetUid: String) async throws -> Bool {
        let snap = try await db.collection(FirestoreCollection.users.rawValue)
            .document(currentUid)
            .collection(FirestoreSubcollection.following.rawValue)
            .document(targetUid)
            .getDocument()
        return snap.exists
    }

    func fetchMarketplaceItems(limit: Int = 120) async throws -> [MarketplaceItemRecord] {
        let snapshot = try await db.collection(FirestoreCollection.marketplaceItems.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            let status = (data["status"] as? String)?.uppercased() ?? "AVAILABLE"
            let isDeleted = (data["isDeleted"] as? Bool) ?? false
            guard !isDeleted else { return nil }
            guard status == "AVAILABLE" || status == "ACTIVE" || status == "OPEN" else { return nil }
            return try? doc.data(as: MarketplaceItemRecord.self)
        }
        .sorted {
            let l = $0.timestamp?.dateValue() ?? .distantPast
            let r = $1.timestamp?.dateValue() ?? .distantPast
            return l > r
        }
    }

    func createMarketplaceItem(
        user: AppSessionUser,
        title: String,
        description: String,
        category: String,
        price: Double
    ) async throws {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty, !cleanDescription.isEmpty else { return }

        let userDoc = try await db.collection(FirestoreCollection.users.rawValue).document(user.uid).getDocument()
        let userData = userDoc.data() ?? [:]
        let sellerName = (userData["name"] as? String) ?? (userData["username"] as? String) ?? (user.email ?? "Seller")
        let sellerPhone = (userData["phoneNumber"] as? String) ?? ""

        try await db.collection(FirestoreCollection.marketplaceItems.rawValue)
            .document()
            .setData([
                "title": cleanTitle,
                "description": cleanDescription,
                "price": price,
                "category": category,
                "sellerName": sellerName,
                "sellerId": user.uid,
                "sellerPhone": sellerPhone,
                "imageUrls": [],
                "locationName": "Global",
                "countryCode": "INT",
                "latitude": 0.0,
                "longitude": 0.0,
                "status": "AVAILABLE",
                "timestamp": FieldValue.serverTimestamp()
            ])
    }

    func fetchAdvertisements(limit: Int = 120) async throws -> [AdvertisementRecord] {
        let snapshot = try await db.collection(FirestoreCollection.advertisements.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: AdvertisementRecord.self)
        }
        .sorted {
            let l = $0.timestamp?.dateValue() ?? .distantPast
            let r = $1.timestamp?.dateValue() ?? .distantPast
            return l > r
        }
    }

    func fetchGarageSales(limit: Int = 120) async throws -> [GarageSaleRecord] {
        let snapshot = try await db.collection(FirestoreCollection.garageSales.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: GarageSaleRecord.self)
        }
        .sorted {
            let l = $0.timestamp?.dateValue() ?? .distantPast
            let r = $1.timestamp?.dateValue() ?? .distantPast
            return l > r
        }
    }

    private func isActiveCommunityDoc(_ data: [String: Any]) -> Bool {
        if (data["isDeleted"] as? Bool) == true { return false }
        let status = (data["status"] as? String)?.uppercased() ?? "ACTIVE"
        if ["DELETED", "REMOVED", "INACTIVE", "REJECTED", "HIDDEN", "ARCHIVED", "CLOSED", "DISABLED"].contains(status) {
            return false
        }
        return true
    }

    private func postDate(_ post: MindLoomPostRecord) -> Date {
        post.timestamp?.dateValue() ?? .distantPast
    }
}
