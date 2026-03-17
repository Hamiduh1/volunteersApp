import Foundation
import FirebaseFirestore
import FirebaseStorage

struct BlindDateOverviewRecord {
    let status: BlindDateUserStatusRecord
    let isStaffExempt: Bool
    let profiles: [BlindDateProfileRecord]
    let receivedInvitations: [BlindDateInvitationRecord]
    let sentInvitations: [BlindDateInvitationRecord]
    let invitationTimeline: [BlindDateTimelineItemRecord]
}

struct DateMediaUpload {
    let data: Data
    let fileExtension: String
    let contentType: String
}

final class DateRepository {
    private let db = Firestore.firestore()
    private let storage = Storage.storage()

    private let staffRoles = Set(["owner", "admin", "associate", "support", "support_associate"])

    func fetchDatingProfiles(currentUid: String?) async throws -> ([DatingProfileRecord], DatingProfileRecord?) {
        let snapshot = try await db.collection(FirestoreCollection.datingProfiles.rawValue)
            .order(by: "createdAt", descending: true)
            .limit(to: 200)
            .getDocuments()

        let rawProfiles = snapshot.documents.compactMap(parseDatingProfile)
        let activeIds = try await filterActiveRegisteredUserIds(userIds: rawProfiles.map(\.uid))
        let profiles = rawProfiles.filter { activeIds.contains($0.uid) }
        let myProfile = currentUid.flatMap { uid in
            profiles.first(where: { $0.uid == uid })
        }
        return (profiles, myProfile)
    }

    func saveDatingProfile(
        uid: String,
        name: String,
        bio: String,
        gender: DatingGender,
        lookingFor: DatingLookingFor,
        phone: String,
        country: String,
        existingImageUrls: [String],
        newImageData: [Data]
    ) async throws {
        var uploadedUrls = existingImageUrls
        for data in newImageData {
            let path = "\(StorageFolder.datingImages.rawValue)/\(uid)/\(UUID().uuidString).jpg"
            let url = try await uploadImage(data: data, path: path)
            uploadedUrls.append(url)
        }

        let payload: [String: Any] = [
            "uid": uid,
            "name": name.trimmingCharacters(in: .whitespacesAndNewlines),
            "bio": bio.trimmingCharacters(in: .whitespacesAndNewlines),
            "gender": gender.rawValue,
            "lookingFor": lookingFor.rawValue,
            "phone": phone.trimmingCharacters(in: .whitespacesAndNewlines),
            "country": country.trimmingCharacters(in: .whitespacesAndNewlines),
            "imageUrls": uploadedUrls,
            "createdAt": FieldValue.serverTimestamp()
        ]

        try await db.collection(FirestoreCollection.datingProfiles.rawValue)
            .document(uid)
            .setData(payload, merge: true)
    }

    func deleteDatingProfile(uid: String) async throws {
        try await db.collection(FirestoreCollection.datingProfiles.rawValue)
            .document(uid)
            .delete()
    }

    func fetchBlindDateOverview(uid: String) async throws -> BlindDateOverviewRecord {
        let userDoc = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let role = (userDoc.get("role") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased() ?? ""
        let staffOnboardingStatus = (userDoc.get("staffOnboardingStatus") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
        let isStaffExempt = staffRoles.contains(role) && (staffOnboardingStatus == nil || staffOnboardingStatus == "ACTIVE")

        let blindProfileDoc = try await db.collection(FirestoreCollection.blindDateProfiles.rawValue)
            .document(uid)
            .getDocument()
        let status = blindDateStatus(from: blindProfileDoc.get("status") as? String)

        async let activeProfilesTask = fetchActiveBlindDateProfiles(currentUid: uid)
        async let receivedTask = fetchReceivedBlindDateInvitations(uid: uid)
        async let sentTask = fetchSentBlindDateInvitations(uid: uid)

        let activeProfiles = try await activeProfilesTask
        let received = try await receivedTask
        let sent = try await sentTask
        let timeline = buildTimeline(received: received, sent: sent)

        return BlindDateOverviewRecord(
            status: status,
            isStaffExempt: isStaffExempt,
            profiles: activeProfiles,
            receivedInvitations: received.filter { normalizeStatus($0.status) == "pending" },
            sentInvitations: sent,
            invitationTimeline: timeline
        )
    }

    func joinBlindDate(uid: String, mediaData: [DateMediaUpload], bio: String, gender: DatingGender) async throws -> Bool {
        var mediaUrls: [String] = []
        for media in mediaData {
            let path = "\(StorageFolder.blindDateMedia.rawValue)/\(uid)/\(UUID().uuidString).\(media.fileExtension)"
            let url = try await uploadBinary(data: media.data, path: path, contentType: media.contentType)
            mediaUrls.append(url)
        }

        let response = try await FunctionsService.shared.callMap(
            function: .joinBlindDate,
            data: [
                "mediaUrls": mediaUrls,
                "bio": bio.trimmingCharacters(in: .whitespacesAndNewlines),
                "gender": gender.rawValue
            ]
        )
        return (response["charged"] as? NSNumber)?.boolValue ?? true
    }

    func rejoinBlindDate() async throws -> Bool {
        let response = try await FunctionsService.shared.callMap(function: .rejoinBlindDate)
        return (response["charged"] as? NSNumber)?.boolValue ?? true
    }

    func acceptBlindDateInvitation(senderId: String) async throws -> String {
        let response = try await FunctionsService.shared.callMap(
            function: .acceptBlindDateInvitation,
            data: ["senderId": senderId]
        )
        guard let chatId = response["chatId"] as? String, !chatId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw NSError(
                domain: "DateRepository",
                code: 5001,
                userInfo: [NSLocalizedDescriptionKey: "Cloud function did not return a valid chat ID."]
            )
        }
        return chatId
    }

    func declineBlindDateInvitation(senderId: String) async throws {
        _ = try await FunctionsService.shared.call(
            function: .declineBlindDateInvitation,
            data: ["senderId": senderId]
        )
    }

    func sendBlindDateInvitation(currentUid: String, recipientId: String) async throws {
        guard !recipientId.isEmpty, recipientId != currentUid else {
            throw NSError(
                domain: "DateRepository",
                code: 5002,
                userInfo: [NSLocalizedDescriptionKey: "Invalid invitation target."]
            )
        }

        let senderProfileDoc = try await db.collection(FirestoreCollection.blindDateProfiles.rawValue)
            .document(currentUid)
            .getDocument()
        let recipientProfileDoc = try await db.collection(FirestoreCollection.blindDateProfiles.rawValue)
            .document(recipientId)
            .getDocument()

        let senderName = (senderProfileDoc.get("name") as? String)?.nonEmpty ?? "Anonymous User"
        let senderPicture = (senderProfileDoc.get("profilePictureUrl") as? String) ?? ""
        let recipientName = (recipientProfileDoc.get("name") as? String)?.nonEmpty ?? "Unknown User"
        let recipientPicture = (recipientProfileDoc.get("profilePictureUrl") as? String) ?? ""

        let incomingRef = db.collection(FirestoreCollection.users.rawValue)
            .document(recipientId)
            .collection(FirestoreSubcollection.blindDateInvitations.rawValue)
            .document(currentUid)
        let sentRef = db.collection(FirestoreCollection.users.rawValue)
            .document(currentUid)
            .collection(FirestoreSubcollection.blindDateSentInvitations.rawValue)
            .document(recipientId)

        let existingIncoming = try await incomingRef.getDocument()
        if existingIncoming.exists,
           normalizeStatus(existingIncoming.get("status") as? String) == "pending" {
            throw NSError(
                domain: "DateRepository",
                code: 5003,
                userInfo: [NSLocalizedDescriptionKey: "Invitation already pending."]
            )
        }

        let payload: [String: Any] = [
            "senderId": currentUid,
            "senderName": senderName,
            "senderProfilePictureUrl": senderPicture,
            "recipientId": recipientId,
            "recipientName": recipientName,
            "recipientProfilePictureUrl": recipientPicture,
            "status": "pending",
            "sentAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp(),
            "respondedAt": NSNull()
        ]

        let batch = db.batch()
        batch.setData(payload, forDocument: incomingRef, merge: true)
        batch.setData(payload, forDocument: sentRef, merge: true)
        try await batch.commit()
    }

    func sendDatingChatInvitation(currentUid: String, recipientId: String) async throws {
        guard !recipientId.isEmpty, recipientId != currentUid else {
            throw NSError(
                domain: "DateRepository",
                code: 5004,
                userInfo: [NSLocalizedDescriptionKey: "Invalid chat invitation target."]
            )
        }

        let currentUserDoc = try await db.collection(FirestoreCollection.users.rawValue)
            .document(currentUid)
            .getDocument()
        let senderName =
            (currentUserDoc.get("name") as? String)?.nonEmpty
            ?? (currentUserDoc.get("username") as? String)?.nonEmpty
            ?? "A User"
        let senderPic = (currentUserDoc.get("profileImageUrl") as? String) ?? ""

        let inviteRef = db.collection(FirestoreCollection.users.rawValue)
            .document(recipientId)
            .collection(FirestoreSubcollection.invitations.rawValue)
            .document(currentUid)

        let existing = try await inviteRef.getDocument()
        if existing.exists {
            throw NSError(
                domain: "DateRepository",
                code: 5005,
                userInfo: [NSLocalizedDescriptionKey: "Invitation already sent to this user."]
            )
        }

        try await inviteRef.setData(
            [
                "senderId": currentUid,
                "senderName": senderName,
                "senderProfilePicUrl": senderPic,
                "status": "pending",
                "timestamp": FieldValue.serverTimestamp(),
                "context": "Dating Loop Profile"
            ],
            merge: true
        )
    }

    private func fetchActiveBlindDateProfiles(currentUid: String) async throws -> [BlindDateProfileRecord] {
        let snapshot = try await db.collection(FirestoreCollection.blindDateProfiles.rawValue)
            .whereField("status", in: ["active", "ACTIVE"])
            .limit(to: 200)
            .getDocuments()

        let raw = snapshot.documents
            .compactMap(parseBlindDateProfile)
            .filter { $0.userId != currentUid }
        let activeIds = try await filterActiveRegisteredUserIds(userIds: raw.map(\.userId))
        return raw.filter { activeIds.contains($0.userId) }
    }

    private func fetchReceivedBlindDateInvitations(uid: String) async throws -> [BlindDateInvitationRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.blindDateInvitations.rawValue)
            .limit(to: 200)
            .getDocuments()

        return snapshot.documents.compactMap { parseBlindDateInvitation($0, defaultSenderId: $0.documentID, defaultRecipientId: uid) }
            .sorted(by: inviteSort)
    }

    private func fetchSentBlindDateInvitations(uid: String) async throws -> [BlindDateInvitationRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.blindDateSentInvitations.rawValue)
            .limit(to: 200)
            .getDocuments()

        return snapshot.documents.compactMap { parseBlindDateInvitation($0, defaultSenderId: uid, defaultRecipientId: $0.documentID) }
            .sorted(by: inviteSort)
    }

    private func buildTimeline(
        received: [BlindDateInvitationRecord],
        sent: [BlindDateInvitationRecord]
    ) -> [BlindDateTimelineItemRecord] {
        let receivedTimeline = received.map { invite in
            BlindDateTimelineItemRecord(
                id: "received:\(invite.senderId)",
                direction: .received,
                otherUserId: invite.senderId,
                otherUserName: invite.senderName.nonEmpty ?? "Unknown user",
                status: normalizeStatus(invite.status),
                sentAt: invite.sentAt,
                updatedAt: invite.updatedAt ?? invite.sentAt
            )
        }
        let sentTimeline = sent.map { invite in
            BlindDateTimelineItemRecord(
                id: "sent:\(invite.recipientId)",
                direction: .sent,
                otherUserId: invite.recipientId,
                otherUserName: invite.recipientName.nonEmpty ?? "Unknown user",
                status: normalizeStatus(invite.status),
                sentAt: invite.sentAt,
                updatedAt: invite.updatedAt ?? invite.sentAt
            )
        }

        return (receivedTimeline + sentTimeline)
            .sorted { (lhs, rhs) in
                let l = lhs.updatedAt ?? lhs.sentAt ?? .distantPast
                let r = rhs.updatedAt ?? rhs.sentAt ?? .distantPast
                return l > r
            }
    }

    private func parseDatingProfile(_ doc: QueryDocumentSnapshot) -> DatingProfileRecord? {
        let data = doc.data()
        let uid = (data["uid"] as? String)?.nonEmpty ?? doc.documentID
        guard !uid.isEmpty else { return nil }
        return DatingProfileRecord(
            id: doc.documentID,
            uid: uid,
            name: (data["name"] as? String) ?? "",
            bio: (data["bio"] as? String) ?? "",
            gender: (data["gender"] as? String) ?? "",
            lookingFor: (data["lookingFor"] as? String) ?? "",
            phone: (data["phone"] as? String) ?? "",
            country: (data["country"] as? String) ?? "",
            imageUrls: (data["imageUrls"] as? [String]) ?? [],
            createdAt: (data["createdAt"] as? Timestamp)?.dateValue()
        )
    }

    private func parseBlindDateProfile(_ doc: QueryDocumentSnapshot) -> BlindDateProfileRecord? {
        let data = doc.data()
        let userId = (data["userId"] as? String)?.nonEmpty ?? doc.documentID
        guard !userId.isEmpty else { return nil }
        let media = (data["media"] as? [String])
            ?? (data["mediaUrls"] as? [String])
            ?? []
        return BlindDateProfileRecord(
            id: doc.documentID,
            userId: userId,
            name: (data["name"] as? String) ?? "Unknown",
            gender: (data["gender"] as? String) ?? "OTHER",
            profilePictureUrl: (data["profilePictureUrl"] as? String) ?? "",
            media: media,
            bio: (data["bio"] as? String) ?? "",
            postedAt: (data["postedAt"] as? Timestamp)?.dateValue(),
            status: (data["status"] as? String) ?? "active"
        )
    }

    private func parseBlindDateInvitation(
        _ doc: QueryDocumentSnapshot,
        defaultSenderId: String,
        defaultRecipientId: String
    ) -> BlindDateInvitationRecord? {
        let data = doc.data()
        let senderId = (data["senderId"] as? String)?.nonEmpty ?? defaultSenderId
        let recipientId = (data["recipientId"] as? String)?.nonEmpty ?? defaultRecipientId
        return BlindDateInvitationRecord(
            id: doc.documentID,
            senderId: senderId,
            senderName: (data["senderName"] as? String) ?? "",
            senderProfilePictureUrl: (data["senderProfilePictureUrl"] as? String) ?? "",
            recipientId: recipientId,
            recipientName: (data["recipientName"] as? String) ?? "",
            recipientProfilePictureUrl: (data["recipientProfilePictureUrl"] as? String) ?? "",
            status: (data["status"] as? String) ?? "pending",
            sentAt: (data["sentAt"] as? Timestamp)?.dateValue(),
            updatedAt: (data["updatedAt"] as? Timestamp)?.dateValue(),
            respondedAt: (data["respondedAt"] as? Timestamp)?.dateValue()
        )
    }

    private func blindDateStatus(from raw: String?) -> BlindDateUserStatusRecord {
        let normalized = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        switch normalized {
        case "active":
            return .active
        case "matched":
            return .matched
        case "expired":
            return .expired
        default:
            return .notJoined
        }
    }

    private func normalizeStatus(_ raw: String?) -> String {
        let status = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        return status.isEmpty ? "pending" : status
    }

    private func inviteSort(_ lhs: BlindDateInvitationRecord, _ rhs: BlindDateInvitationRecord) -> Bool {
        let l = lhs.updatedAt ?? lhs.sentAt ?? .distantPast
        let r = rhs.updatedAt ?? rhs.sentAt ?? .distantPast
        return l > r
    }

    private func uploadImage(data: Data, path: String) async throws -> String {
        let ref = storage.reference().child(path)
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"

        _ = try await withCheckedThrowingContinuation { continuation in
            ref.putData(data, metadata: metadata) { meta, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: meta)
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
                            domain: "DateRepository",
                            code: 5006,
                            userInfo: [NSLocalizedDescriptionKey: "Missing download URL"]
                        )
                    )
                }
            }
        }
        return url.absoluteString
    }

    private func uploadBinary(data: Data, path: String, contentType: String) async throws -> String {
        let ref = storage.reference().child(path)
        let metadata = StorageMetadata()
        metadata.contentType = contentType

        _ = try await withCheckedThrowingContinuation { continuation in
            ref.putData(data, metadata: metadata) { meta, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: meta)
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
                            domain: "DateRepository",
                            code: 5007,
                            userInfo: [NSLocalizedDescriptionKey: "Missing download URL"]
                        )
                    )
                }
            }
        }
        return url.absoluteString
    }

    private func filterActiveRegisteredUserIds(userIds: [String]) async throws -> Set<String> {
        let candidates = Array(Set(userIds.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }))
        if candidates.isEmpty { return [] }

        var activeIds = Set<String>()
        for chunk in candidates.chunked(into: 30) {
            let usersSnapshot = try await db.collection(FirestoreCollection.users.rawValue)
                .whereField(FieldPath.documentID(), in: chunk)
                .getDocuments()

            for doc in usersSnapshot.documents {
                let role = ((doc.get("role") as? String) ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .lowercased()
                let profileStatus = ((doc.get("profileStatus") as? String) ?? "active")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .lowercased()
                let staffOnboardingStatus = (doc.get("staffOnboardingStatus") as? String)?
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .uppercased()
                let isStaffRole = staffRoles.contains(role)
                let isActiveRegistered = profileStatus == "active"
                    && (!isStaffRole || staffOnboardingStatus == nil || staffOnboardingStatus == "ACTIVE")
                if isActiveRegistered {
                    activeIds.insert(doc.documentID)
                }
            }
        }
        return activeIds
    }
}

private extension String {
    var nonEmpty: String? {
        let clean = trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? nil : clean
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        guard size > 0 else { return [self] }
        var result: [[Element]] = []
        var index = 0
        while index < count {
            let end = Swift.min(index + size, count)
            result.append(Array(self[index..<end]))
            index += size
        }
        return result
    }
}
