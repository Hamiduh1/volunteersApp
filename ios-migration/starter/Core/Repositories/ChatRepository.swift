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

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: ChatConversationRecord.self)
        }
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

        return snapshot.documents.compactMap { doc in
            try? doc.data(as: ChatMessageRecord.self)
        }
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
        let userInvites = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.invitations.rawValue)
            .limit(to: limit)
            .getDocuments()

        let chatInvites = try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.chatInvitations.rawValue)
            .limit(to: limit)
            .getDocuments()

        var merged: [String: UserInvitationRecord] = [:]
        for doc in userInvites.documents {
            let data = doc.data()
            let invite = UserInvitationRecord(
                id: doc.documentID,
                senderId: (data["senderId"] as? String) ?? doc.documentID,
                senderName: data["senderName"] as? String,
                inviterName: nil,
                senderEmail: data["senderEmail"] as? String,
                senderProfileImageUrl: (data["senderProfileImageUrl"] as? String) ?? (data["senderProfilePicUrl"] as? String),
                status: data["status"] as? String,
                source: "chat",
                context: data["context"] as? String,
                timestamp: data["timestamp"] as? Timestamp
            )
            merged["chat:\(doc.reference.path)"] = invite
        }

        for doc in chatInvites.documents {
            let data = doc.data()
            let invite = UserInvitationRecord(
                id: doc.documentID,
                senderId: doc.documentID,
                senderName: nil,
                inviterName: data["inviterName"] as? String,
                senderEmail: nil,
                senderProfileImageUrl: nil,
                status: data["status"] as? String,
                source: "legacy_chat",
                context: "Volunteer Directory",
                timestamp: data["timestamp"] as? Timestamp
            )
            merged["legacy:\(doc.reference.path)"] = invite
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
}
