import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift
import FirebaseStorage

final class CommunityRepository {
    private let db = Firestore.firestore()
    private let storage = Storage.storage()

    func fetchMindLoomPosts(limit: Int = 120) async throws -> [MindLoomPostRecord] {
        let snapshot = try await db.collectionGroup(FirestoreCollectionGroup.jokes.rawValue)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: MindLoomPostRecord.self)
        }
    }

    func fetchMindLoomPosts(authorId: String, limit: Int = 120) async throws -> [MindLoomPostRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(authorId)
            .collection(FirestoreSubcollection.jokes.rawValue)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: MindLoomPostRecord.self)
        }
    }

    func updateMindLoomPost(authorUid: String, postId: String, newText: String) async throws {
        let cleanAuthorUid = authorUid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPostId = postId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanText = newText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanAuthorUid.isEmpty, !cleanPostId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9020,
                userInfo: [NSLocalizedDescriptionKey: "Post reference is missing."]
            )
        }
        guard !cleanText.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9021,
                userInfo: [NSLocalizedDescriptionKey: "Post text cannot be empty."]
            )
        }

        try await db.collection(FirestoreCollection.users.rawValue)
            .document(cleanAuthorUid)
            .collection(FirestoreSubcollection.jokes.rawValue)
            .document(cleanPostId)
            .updateData(["text": cleanText])
    }

    func deleteMindLoomPost(authorUid: String, postId: String) async throws {
        let cleanAuthorUid = authorUid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPostId = postId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanAuthorUid.isEmpty, !cleanPostId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9022,
                userInfo: [NSLocalizedDescriptionKey: "Post reference is missing."]
            )
        }

        try await db.collection(FirestoreCollection.users.rawValue)
            .document(cleanAuthorUid)
            .collection(FirestoreSubcollection.jokes.rawValue)
            .document(cleanPostId)
            .delete()
    }

    func fetchMindLoomComments(post: MindLoomPostRecord, limit: Int = 250) async throws -> [MindLoomCommentRecord] {
        let authorId = (post.authorId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let postId = (post.id ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !authorId.isEmpty, !postId.isEmpty else { return [] }

        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(authorId)
            .collection(FirestoreSubcollection.jokes.rawValue)
            .document(postId)
            .collection(FirestoreSubcollection.comments.rawValue)
            .order(by: "timestamp", descending: false)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            if let decoded = try? doc.data(as: MindLoomCommentRecord.self) {
                return decoded
            }
            let data = doc.data()
            return MindLoomCommentRecord(
                id: doc.documentID,
                authorId: data["authorId"] as? String,
                authorName: data["authorName"] as? String,
                authorProfileUrl: data["authorProfileUrl"] as? String,
                text: data["text"] as? String,
                isOwnerResponse: data["isOwnerResponse"] as? Bool,
                timestamp: data["timestamp"] as? Timestamp
            )
        }
    }

    func postMindLoomComment(user: AppSessionUser, post: MindLoomPostRecord, text: String) async throws {
        let cleanText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanText.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9023,
                userInfo: [NSLocalizedDescriptionKey: "Comment text cannot be empty."]
            )
        }

        let authorId = (post.authorId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let postId = (post.id ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !authorId.isEmpty, !postId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9024,
                userInfo: [NSLocalizedDescriptionKey: "Post reference is missing."]
            )
        }

        let (displayName, profileUrl) = try await fetchUserIdentity(user: user)
        let jokeRef = db.collection(FirestoreCollection.users.rawValue)
            .document(authorId)
            .collection(FirestoreSubcollection.jokes.rawValue)
            .document(postId)
        let commentRef = jokeRef.collection(FirestoreSubcollection.comments.rawValue).document()

        let batch = db.batch()
        batch.setData([
            "authorId": user.uid,
            "authorName": displayName,
            "authorProfileUrl": profileUrl as Any,
            "text": cleanText,
            "timestamp": FieldValue.serverTimestamp()
        ], forDocument: commentRef, merge: true)
        batch.updateData(["commentsCount": FieldValue.increment(Int64(1))], forDocument: jokeRef)
        try await batch.commit()
    }

    func createMindLoomPost(
        user: AppSessionUser,
        text: String,
        attachment: CommunityAttachmentDraft?
    ) async throws {
        let cleanText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanText.isEmpty || attachment != nil else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9001,
                userInfo: [NSLocalizedDescriptionKey: "Enter text or attach media before posting."]
            )
        }

        let (displayName, profileUrl) = try await fetchUserIdentity(user: user)

        var mediaType = "TEXT"
        var mediaUrl: String?

        if let attachment {
            mediaType = attachment.type.rawValue.uppercased()
            mediaUrl = try await uploadMindLoomAttachment(ownerUid: user.uid, attachment: attachment)
        }

        let postRef = db.collection(FirestoreCollection.users.rawValue)
            .document(user.uid)
            .collection(FirestoreSubcollection.jokes.rawValue)
            .document()

        try await postRef.setData(
            [
                "authorId": user.uid,
                "authorName": displayName,
                "authorProfileUrl": profileUrl as Any,
                "text": cleanText,
                "mediaUrl": mediaUrl as Any,
                "mediaType": mediaType,
                "status": "ACTIVE",
                "likes": [],
                "commentsCount": 0,
                "timestamp": FieldValue.serverTimestamp()
            ],
            merge: true
        )
    }

    func toggleMindLoomLike(post: MindLoomPostRecord, uid: String) async throws {
        guard let postId = post.id, !postId.isEmpty else { return }
        let authorId = (post.authorId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !authorId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9002,
                userInfo: [NSLocalizedDescriptionKey: "Cannot like this post because author information is missing."]
            )
        }

        let likes = post.likes ?? []
        let ref = db.collection(FirestoreCollection.users.rawValue)
            .document(authorId)
            .collection(FirestoreSubcollection.jokes.rawValue)
            .document(postId)

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

    func fetchFollowingIds(currentUid: String, limit: Int = 500) async throws -> Set<String> {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(currentUid)
            .collection(FirestoreSubcollection.following.rawValue)
            .limit(to: limit)
            .getDocuments()
        return Set(snapshot.documents.map { $0.documentID })
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
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: AdvertisementRecord.self)
        }
    }

    func createAdvertisement(
        user: AppSessionUser,
        title: String,
        description: String,
        targetUrl: String,
        ownerPhone: String,
        media: [CommunityAttachmentDraft],
        adCost: Double = 5.0
    ) async throws {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanTargetUrl = targetUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanOwnerPhone = ownerPhone.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanTitle.isEmpty, !cleanDescription.isEmpty, !cleanTargetUrl.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9003,
                userInfo: [NSLocalizedDescriptionKey: "Title, description, and target URL are required."]
            )
        }
        guard !media.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9004,
                userInfo: [NSLocalizedDescriptionKey: "Add media before publishing an ad."]
            )
        }

        let (displayName, _) = try await fetchUserIdentity(user: user)
        let sponsorName = displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Volunteer App Partner"
            : displayName
        var imageUrls: [String] = []
        var mediaPayload: [[String: String]] = []
        for item in media {
            let uploadedUrl = try await uploadGenericAttachment(
                ownerUid: user.uid,
                attachment: item,
                rootFolder: StorageFolder.ads.rawValue
            )
            mediaPayload.append([
                "url": uploadedUrl,
                "type": item.type.storageType,
                "name": item.fileName
            ])
            if item.type == .image {
                imageUrls.append(uploadedUrl)
            }
        }

        let adData: [String: Any] = [
            "title": cleanTitle,
            "description": cleanDescription,
            "targetUrl": cleanTargetUrl,
            "ownerPhone": cleanOwnerPhone,
            "mediaUrls": imageUrls,
            "media": mediaPayload,
            "sponsor": sponsorName,
            "ownerId": user.uid,
            "timestamp": FieldValue.serverTimestamp()
        ]

        let historyData: [String: Any] = [
            "title": "Posted Ad: \(cleanTitle)",
            "amount": adCost,
            "type": "DEBIT",
            "status": "COMPLETED",
            "timestamp": FieldValue.serverTimestamp()
        ]

        try await runAdvertisementPostingTransaction(userUid: user.uid, adData: adData, historyData: historyData, adCost: adCost)
    }

    func updateAdvertisement(
        user: AppSessionUser,
        adId: String,
        title: String,
        description: String,
        targetUrl: String,
        ownerPhone: String,
        newMedia: [CommunityAttachmentDraft]
    ) async throws {
        let cleanAdId = adId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanAdId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9010,
                userInfo: [NSLocalizedDescriptionKey: "Advertisement ID is missing."]
            )
        }

        var updates: [String: Any] = [
            "title": title.trimmingCharacters(in: .whitespacesAndNewlines),
            "description": description.trimmingCharacters(in: .whitespacesAndNewlines),
            "targetUrl": targetUrl.trimmingCharacters(in: .whitespacesAndNewlines),
            "ownerPhone": ownerPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        ]

        if !newMedia.isEmpty {
            var imageUrls: [String] = []
            var mediaPayload: [[String: String]] = []
            for item in newMedia {
                let uploadedUrl = try await uploadGenericAttachment(
                    ownerUid: user.uid,
                    attachment: item,
                    rootFolder: StorageFolder.ads.rawValue
                )
                mediaPayload.append([
                    "url": uploadedUrl,
                    "type": item.type.storageType,
                    "name": item.fileName
                ])
                if item.type == .image {
                    imageUrls.append(uploadedUrl)
                }
            }
            updates["mediaUrls"] = imageUrls
            updates["media"] = mediaPayload
        }

        try await db.collection(FirestoreCollection.advertisements.rawValue)
            .document(cleanAdId)
            .updateData(updates)
    }

    func deleteAdvertisement(adId: String) async throws {
        let cleanAdId = adId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanAdId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9011,
                userInfo: [NSLocalizedDescriptionKey: "Advertisement ID is missing."]
            )
        }

        try await db.collection(FirestoreCollection.advertisements.rawValue)
            .document(cleanAdId)
            .delete()
    }

    func fetchGarageSales(limit: Int = 120) async throws -> [GarageSaleRecord] {
        let snapshot = try await db.collection(FirestoreCollection.garageSales.rawValue)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard isActiveCommunityDoc(data) else { return nil }
            return try? doc.data(as: GarageSaleRecord.self)
        }
    }

    func createGarageSale(
        user: AppSessionUser,
        title: String,
        description: String,
        contactName: String,
        contactPhone: String,
        contactEmail: String,
        address: String,
        city: String,
        state: String,
        postalCode: String,
        latitude: Double?,
        longitude: Double?,
        media: [CommunityAttachmentDraft]
    ) async throws {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanContactName = contactName.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanTitle.isEmpty, !cleanDescription.isEmpty, !cleanContactName.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9005,
                userInfo: [NSLocalizedDescriptionKey: "Title, description, and contact name are required."]
            )
        }

        var mediaPayload: [[String: String]] = []
        for item in media {
            let uploadedUrl = try await uploadGenericAttachment(
                ownerUid: user.uid,
                attachment: item,
                rootFolder: StorageFolder.garageSales.rawValue
            )
            mediaPayload.append([
                "url": uploadedUrl,
                "type": item.type.storageType,
                "name": item.fileName
            ])
        }

        try await db.collection(FirestoreCollection.garageSales.rawValue)
            .document()
            .setData([
                "title": cleanTitle,
                "description": cleanDescription,
                "contactName": cleanContactName,
                "contactPhone": contactPhone.trimmingCharacters(in: .whitespacesAndNewlines),
                "contactEmail": contactEmail.trimmingCharacters(in: .whitespacesAndNewlines),
                "address": address.trimmingCharacters(in: .whitespacesAndNewlines),
                "city": city.trimmingCharacters(in: .whitespacesAndNewlines),
                "state": state.trimmingCharacters(in: .whitespacesAndNewlines),
                "postalCode": postalCode.trimmingCharacters(in: .whitespacesAndNewlines),
                "latitude": latitude as Any,
                "longitude": longitude as Any,
                "media": mediaPayload,
                "ownerId": user.uid,
                "timestamp": FieldValue.serverTimestamp()
            ])
    }

    func submitGarageSalePayment(
        buyerUid: String,
        sellerUid: String,
        garageSaleId: String,
        amount: Double
    ) async throws {
        let cleanBuyerUid = buyerUid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSellerUid = sellerUid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanGarageSaleId = garageSaleId.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanBuyerUid.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9012,
                userInfo: [NSLocalizedDescriptionKey: "You must be logged in."]
            )
        }
        guard !cleanSellerUid.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9013,
                userInfo: [NSLocalizedDescriptionKey: "Seller information is missing."]
            )
        }
        guard !cleanGarageSaleId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9014,
                userInfo: [NSLocalizedDescriptionKey: "Garage sale ID is missing."]
            )
        }
        guard amount > 0 else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9015,
                userInfo: [NSLocalizedDescriptionKey: "Enter a valid amount."]
            )
        }

        try await db.collection(FirestoreCollection.garageSalePayments.rawValue)
            .document()
            .setData([
                "buyerId": cleanBuyerUid,
                "sellerId": cleanSellerUid,
                "garageSaleId": cleanGarageSaleId,
                "amount": amount,
                "status": "pending",
                "timestamp": FieldValue.serverTimestamp()
            ])
    }

    func sendSponsoredChatInvitation(
        sender: AppSessionUser,
        recipientId: String,
        contextLabel: String,
        duplicateMessage: String
    ) async throws -> String {
        let cleanRecipientId = recipientId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanRecipientId.isEmpty else {
            throw NSError(
                domain: "CommunityRepository",
                code: 9016,
                userInfo: [NSLocalizedDescriptionKey: "Recipient information is missing."]
            )
        }

        let invitationRef = db.collection(FirestoreCollection.users.rawValue)
            .document(cleanRecipientId)
            .collection(FirestoreSubcollection.invitations.rawValue)
            .document(sender.uid)

        let existing = try await invitationRef.getDocument()
        if existing.exists {
            return duplicateMessage
        }

        let (senderName, senderProfileImageUrl) = try await fetchUserIdentity(user: sender)

        try await invitationRef.setData(
            [
                "senderId": sender.uid,
                "senderName": senderName,
                "senderProfilePicUrl": senderProfileImageUrl ?? "",
                "senderProfileImageUrl": senderProfileImageUrl ?? "",
                "status": "pending",
                "timestamp": FieldValue.serverTimestamp(),
                "context": contextLabel
            ],
            merge: true
        )

        return "Chat invitation sent!"
    }

    private func runAdvertisementPostingTransaction(
        userUid: String,
        adData: [String: Any],
        historyData: [String: Any],
        adCost: Double
    ) async throws {
        let userRef = db.collection(FirestoreCollection.users.rawValue).document(userUid)

        try await withCheckedThrowingContinuation { continuation in
            db.runTransaction({ transaction, errorPointer -> Any? in
                do {
                    let userSnapshot = try transaction.getDocument(userRef)
                    guard let userData = userSnapshot.data() else {
                        throw NSError(
                            domain: "CommunityRepository",
                            code: 9006,
                            userInfo: [NSLocalizedDescriptionKey: "User profile not found."]
                        )
                    }
                    guard let wallet = userData["wallet"] as? [String: Any] else {
                        throw NSError(
                            domain: "CommunityRepository",
                            code: 9007,
                            userInfo: [NSLocalizedDescriptionKey: "Wallet not initialized."]
                        )
                    }

                    let currentBalance = (wallet["balance"] as? NSNumber)?.doubleValue ?? 0
                    if currentBalance < adCost {
                        throw NSError(
                            domain: "CommunityRepository",
                            code: 9008,
                            userInfo: [NSLocalizedDescriptionKey: "Insufficient funds. Posting an ad costs \(adCost)."]
                        )
                    }

                    let adRef = self.db.collection(FirestoreCollection.advertisements.rawValue).document()
                    let historyRef = userRef.collection(FirestoreSubcollection.transactions.rawValue).document()

                    transaction.updateData(["wallet.balance": currentBalance - adCost], forDocument: userRef)
                    transaction.setData(adData, forDocument: adRef)
                    transaction.setData(historyData, forDocument: historyRef)
                } catch {
                    errorPointer?.pointee = error as NSError
                }
                return nil
            }) { _, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
    }

    private func fetchUserIdentity(user: AppSessionUser) async throws -> (String, String?) {
        let userDoc = try await db.collection(FirestoreCollection.users.rawValue).document(user.uid).getDocument()
        let data = userDoc.data() ?? [:]
        let displayName = (data["name"] as? String)
            ?? (data["username"] as? String)
            ?? (user.email ?? "User")
        let profileUrl = (data["profileImageUrl"] as? String) ?? (data["profilePictureUrl"] as? String)
        return (displayName, profileUrl)
    }

    private func uploadMindLoomAttachment(ownerUid: String, attachment: CommunityAttachmentDraft) async throws -> String {
        let folder: String
        switch attachment.type {
        case .image:
            folder = StorageFolder.jokeImages.rawValue
        case .video:
            folder = StorageFolder.jokeVideos.rawValue
        case .document:
            folder = StorageFolder.jokeDocs.rawValue
        }
        return try await uploadGenericAttachment(ownerUid: ownerUid, attachment: attachment, rootFolder: folder)
    }

    private func uploadGenericAttachment(
        ownerUid: String,
        attachment: CommunityAttachmentDraft,
        rootFolder: String
    ) async throws -> String {
        let suffix = normalizedExtension(from: attachment.fileName, fallbackType: attachment.type)
        let filePath = "\(rootFolder)/\(ownerUid)/\(UUID().uuidString)\(suffix)"
        let ref = storage.reference().child(filePath)

        let metadata = StorageMetadata()
        metadata.contentType = attachment.contentType

        _ = try await withCheckedThrowingContinuation { continuation in
            ref.putData(attachment.data, metadata: metadata) { result, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: result)
                }
            }
        }

        let url = try await withCheckedThrowingContinuation { continuation in
            ref.downloadURL { url, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let url {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(
                        throwing: NSError(
                            domain: "CommunityRepository",
                            code: 9009,
                            userInfo: [NSLocalizedDescriptionKey: "Upload completed, but download URL was unavailable."]
                        )
                    )
                }
            }
        }

        return url.absoluteString
    }

    private func normalizedExtension(from fileName: String, fallbackType: CommunityAttachmentType) -> String {
        let ext = (fileName as NSString).pathExtension.trimmingCharacters(in: .whitespacesAndNewlines)
        if !ext.isEmpty {
            return ".\(ext.lowercased())"
        }

        switch fallbackType {
        case .image:
            return ".jpg"
        case .video:
            return ".mp4"
        case .document:
            return ".pdf"
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
}
