import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class ChatRepository {
    private let db = Firestore.firestore()

    func fetchConversations(uid: String, limit: Int = 100) async throws -> [ChatConversationRecord] {
        let snapshot = try await db.collection(FirestoreCollection.chats.rawValue)
            .whereField("participants", arrayContains: uid)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap(parseConversation)
        .sorted {
            let l = $0.lastMessageTimestamp?.dateValue() ?? .distantPast
            let r = $1.lastMessageTimestamp?.dateValue() ?? .distantPast
            return l > r
        }
    }

    func fetchMessages(conversationId: String, limit: Int = 150) async throws -> [ChatMessageRecord] {
        let snapshot = try await db.collection(FirestoreCollection.chats.rawValue)
            .document(conversationId)
            .collection(FirestoreSubcollection.messages.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap(parseMessage)
        .sorted {
            let l = $0.timestamp?.dateValue() ?? .distantPast
            let r = $1.timestamp?.dateValue() ?? .distantPast
            return l < r
        }
    }

    func sendMessage(conversationId: String, senderId: String, text: String) async throws {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        let messageRef = db.collection(FirestoreCollection.chats.rawValue)
            .document(conversationId)
            .collection(FirestoreSubcollection.messages.rawValue)
            .document()

        try await messageRef.setData([
            "senderId": senderId,
            "text": text,
            "timestamp": FieldValue.serverTimestamp()
        ])

        try await db.collection(FirestoreCollection.chats.rawValue)
            .document(conversationId)
            .setData([
                "lastMessage": text,
                "lastMessageText": text,
                "lastMessageTimestamp": FieldValue.serverTimestamp()
            ], merge: true)
    }

    func fetchInvitations(uid: String, limit: Int = 100) async throws -> [UserInvitationRecord] {
        var userInvites: QuerySnapshot?
        var chatInvites: QuerySnapshot?
        var lastError: Error?

        do {
            userInvites = try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.invitations.rawValue)
                .limit(to: limit)
                .getDocuments()
        } catch {
            lastError = error
        }

        do {
            chatInvites = try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.chatInvitations.rawValue)
                .limit(to: limit)
                .getDocuments()
        } catch {
            lastError = error
        }

        if userInvites == nil, chatInvites == nil, let lastError {
            throw lastError
        }

        var merged: [String: UserInvitationRecord] = [:]
        for doc in userInvites?.documents ?? [] {
            guard let invite = parseUserInvitation(doc) else { continue }
            let key = "chat:\(invite.id ?? doc.documentID):\(invite.senderId ?? "")"
            merged[key] = invite
        }

        for doc in chatInvites?.documents ?? [] {
            guard let invite = parseLegacyInvitation(doc) else { continue }
            let key = "legacy:\(invite.id ?? doc.documentID):\(invite.senderId ?? "")"
            merged[key] = invite
        }

        return merged.values.sorted {
            let l = $0.timestamp?.dateValue() ?? .distantPast
            let r = $1.timestamp?.dateValue() ?? .distantPast
            return l > r
        }
    }

    func acceptInvitation(uid: String, invitation: UserInvitationRecord) async throws -> String {
        let senderId = invitation.senderId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let invitationId = invitation.id?.trimmingCharacters(in: .whitespacesAndNewlines) ?? senderId
        guard !senderId.isEmpty else {
            throw NSError(
                domain: "ChatRepository",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Invitation sender is missing."]
            )
        }

        if let existingConversationId = try await findExistingConversation(currentUid: uid, otherUid: senderId) {
            try await markInvitationAccepted(uid: uid, invitation: invitation, invitationId: invitationId)
            return existingConversationId
        }

        let conversationRef = db.collection(FirestoreCollection.chats.rawValue).document()
        let chatData: [String: Any] = [
            "chatId": conversationRef.documentID,
            "participants": [uid, senderId],
            "lastMessage": "Chat started! Say hi.",
            "lastMessageText": "Chat started! Say hi.",
            "lastMessageTimestamp": FieldValue.serverTimestamp()
        ]

        let batch = db.batch()
        batch.setData(chatData, forDocument: conversationRef, merge: true)
        addInvitationAcceptMutation(
            batch: batch,
            uid: uid,
            invitation: invitation,
            invitationId: invitationId
        )
        try await batch.commit()
        return conversationRef.documentID
    }

    func declineInvitation(uid: String, invitation: UserInvitationRecord) async throws {
        let senderId = invitation.senderId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let invitationId = invitation.id?.trimmingCharacters(in: .whitespacesAndNewlines) ?? senderId
        guard !invitationId.isEmpty else {
            throw NSError(
                domain: "ChatRepository",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Invitation reference is missing."]
            )
        }

        if (invitation.source ?? "").lowercased() == "legacy_chat" {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.chatInvitations.rawValue)
                .document(invitationId)
                .delete()
        } else {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.invitations.rawValue)
                .document(invitationId)
                .delete()
        }
    }

    private func findExistingConversation(currentUid: String, otherUid: String) async throws -> String? {
        let snapshot = try await db.collection(FirestoreCollection.chats.rawValue)
            .whereField("participants", arrayContains: currentUid)
            .limit(to: 100)
            .getDocuments()

        for doc in snapshot.documents {
            let participants = doc.data()["participants"] as? [String] ?? []
            if participants.contains(otherUid) {
                return doc.documentID
            }
        }
        return nil
    }

    private func markInvitationAccepted(uid: String, invitation: UserInvitationRecord, invitationId: String) async throws {
        if (invitation.source ?? "").lowercased() == "legacy_chat" {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.chatInvitations.rawValue)
                .document(invitationId)
                .delete()
        } else {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.invitations.rawValue)
                .document(invitationId)
                .setData(
                    [
                        "status": "accepted",
                        "lastUpdatedAt": FieldValue.serverTimestamp()
                    ],
                    merge: true
                )
        }
    }

    private func addInvitationAcceptMutation(
        batch: WriteBatch,
        uid: String,
        invitation: UserInvitationRecord,
        invitationId: String
    ) {
        if (invitation.source ?? "").lowercased() == "legacy_chat" {
            let legacyRef = db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.chatInvitations.rawValue)
                .document(invitationId)
            batch.deleteDocument(legacyRef)
        } else {
            let inviteRef = db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.invitations.rawValue)
                .document(invitationId)
            batch.setData(
                [
                    "status": "accepted",
                    "lastUpdatedAt": FieldValue.serverTimestamp()
                ],
                forDocument: inviteRef,
                merge: true
            )
        }
    }

    private func parseConversation(_ doc: QueryDocumentSnapshot) -> ChatConversationRecord? {
        if let decoded = try? doc.data(as: ChatConversationRecord.self) {
            return decoded
        }
        let data = doc.data()
        let lastMessage = data.firstNonEmptyString(keys: ["lastMessage", "lastMessageText", "message"])
        let lastMessageTimestamp = data.firstTimestamp(keys: ["lastMessageTimestamp", "timestamp", "updatedAt", "createdAt"])
        let participants = data["participants"] as? [String]
        return ChatConversationRecord(
            id: doc.documentID,
            participants: participants,
            lastMessage: lastMessage,
            lastMessageText: lastMessage,
            lastMessageTimestamp: lastMessageTimestamp
        )
    }

    private func parseMessage(_ doc: QueryDocumentSnapshot) -> ChatMessageRecord? {
        if let decoded = try? doc.data(as: ChatMessageRecord.self) {
            return decoded
        }
        let data = doc.data()
        let senderId = data.firstNonEmptyString(keys: ["senderId", "fromId", "userId"])
        let text = data.firstNonEmptyString(keys: ["text", "message", "body"])
        let timestamp = data.firstTimestamp(keys: ["timestamp", "createdAt", "sentAt"])
        return ChatMessageRecord(
            id: doc.documentID,
            senderId: senderId,
            text: text,
            timestamp: timestamp
        )
    }

    private func parseUserInvitation(_ doc: QueryDocumentSnapshot) -> UserInvitationRecord? {
        let data = doc.data()
        let senderId = data.firstNonEmptyString(keys: ["senderId", "inviterId", "fromUid"]) ?? doc.documentID
        let timestamp = data.firstTimestamp(keys: ["timestamp", "createdAt", "updatedAt"])
        return UserInvitationRecord(
            id: doc.documentID,
            senderId: senderId,
            senderName: data.firstNonEmptyString(keys: ["senderName", "senderDisplayName", "inviterName"]),
            inviterName: data.firstNonEmptyString(keys: ["inviterName", "senderName"]),
            senderEmail: data.firstNonEmptyString(keys: ["senderEmail", "email"]),
            senderProfileImageUrl: data.firstNonEmptyString(keys: ["senderProfileImageUrl", "senderProfilePicUrl", "senderAvatarUrl"]),
            status: data.firstNonEmptyString(keys: ["status"]) ?? "pending",
            source: data.firstNonEmptyString(keys: ["source"]) ?? "chat",
            context: data.firstNonEmptyString(keys: ["context", "origin"]),
            timestamp: timestamp
        )
    }

    private func parseLegacyInvitation(_ doc: QueryDocumentSnapshot) -> UserInvitationRecord? {
        let data = doc.data()
        let senderId = data.firstNonEmptyString(keys: ["senderId", "inviterId", "fromUid"]) ?? doc.documentID
        let timestamp = data.firstTimestamp(keys: ["timestamp", "createdAt", "updatedAt"])
        return UserInvitationRecord(
            id: doc.documentID,
            senderId: senderId,
            senderName: data.firstNonEmptyString(keys: ["senderName"]),
            inviterName: data.firstNonEmptyString(keys: ["inviterName", "senderName"]),
            senderEmail: data.firstNonEmptyString(keys: ["senderEmail", "email"]),
            senderProfileImageUrl: data.firstNonEmptyString(keys: ["senderProfileImageUrl", "senderProfilePicUrl", "senderAvatarUrl"]),
            status: data.firstNonEmptyString(keys: ["status"]) ?? "pending",
            source: data.firstNonEmptyString(keys: ["source"]) ?? "legacy_chat",
            context: data.firstNonEmptyString(keys: ["context", "origin"]) ?? "Volunteer Directory",
            timestamp: timestamp
        )
    }
}

private extension Dictionary where Key == String, Value == Any {
    func firstNonEmptyString(keys: [String]) -> String? {
        for key in keys {
            if let value = self[key] as? String {
                let clean = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty { return clean }
            }
        }
        return nil
    }

    func firstTimestamp(keys: [String]) -> Timestamp? {
        for key in keys {
            if let timestamp = self[key] as? Timestamp {
                return timestamp
            }
            if let date = self[key] as? Date {
                return Timestamp(date: date)
            }
            if let number = self[key] as? NSNumber {
                let raw = number.doubleValue
                if raw > 1_000_000_000_000 {
                    return Timestamp(date: Date(timeIntervalSince1970: raw / 1000.0))
                }
                if raw > 0 {
                    return Timestamp(date: Date(timeIntervalSince1970: raw))
                }
            }
            if let text = self[key] as? String, let raw = Double(text.trimmingCharacters(in: .whitespacesAndNewlines)) {
                if raw > 1_000_000_000_000 {
                    return Timestamp(date: Date(timeIntervalSince1970: raw / 1000.0))
                }
                if raw > 0 {
                    return Timestamp(date: Date(timeIntervalSince1970: raw))
                }
            }
        }
        return nil
    }
}
