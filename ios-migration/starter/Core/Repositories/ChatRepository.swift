import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class ChatRepository {
    private let db = Firestore.firestore()
    private var userSummaryCache: [String: (name: String, photoUrl: String?)] = [:]

    func fetchDirectoryUsers(currentUid: String, limit: Int = 500) async throws -> [DirectoryUserRecord] {
        let cleanCurrentUid = currentUid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanCurrentUid.isEmpty else { return [] }

        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .order(by: "name", descending: false)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            let uid = data.firstNonEmptyString(keys: ["uid"]) ?? doc.documentID
            guard uid != cleanCurrentUid else { return nil }

            let name = data.firstNonEmptyString(keys: ["name"]) ?? "Anonymous"
            let email = data.firstNonEmptyString(keys: ["email"]) ?? ""
            let username = data.firstNonEmptyString(keys: ["username"]) ?? ""
            let phone = data.firstNonEmptyString(keys: ["phoneNumber", "phone"]) ?? ""
            let profileImageUrl = data.firstNonEmptyString(keys: ["profileImageUrl", "profilePictureUrl", "avatarUrl"])
            return DirectoryUserRecord(
                id: doc.documentID,
                uid: uid,
                name: name,
                email: email,
                username: username,
                phoneNumber: phone,
                profileImageUrl: profileImageUrl
            )
        }
    }

    func sendDirectoryChatInvitation(sender: AppSessionUser, recipient: DirectoryUserRecord) async throws {
        let senderUid = sender.uid.trimmingCharacters(in: .whitespacesAndNewlines)
        let recipientUid = recipient.uid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !senderUid.isEmpty else {
            throw NSError(
                domain: "ChatRepository",
                code: 5,
                userInfo: [NSLocalizedDescriptionKey: "Authentication required."]
            )
        }
        guard !recipientUid.isEmpty else {
            throw NSError(
                domain: "ChatRepository",
                code: 6,
                userInfo: [NSLocalizedDescriptionKey: "Invalid user selection."]
            )
        }

        let invitationRef = db.collection(FirestoreCollection.users.rawValue)
            .document(recipientUid)
            .collection(FirestoreSubcollection.invitations.rawValue)
            .document(senderUid)

        let existing = try await invitationRef.getDocument()
        if existing.exists {
            throw NSError(
                domain: "ChatRepository",
                code: 7,
                userInfo: [NSLocalizedDescriptionKey: "Invitation already sent to \(recipient.name)."]
            )
        }

        let senderSummary = try await fetchUserSummary(uid: senderUid)
        try await invitationRef.setData(
            [
                "senderId": senderUid,
                "senderName": senderSummary.name,
                "senderProfilePicUrl": senderSummary.photoUrl as Any,
                "status": "pending",
                "timestamp": FieldValue.serverTimestamp(),
                "context": "User Directory"
            ],
            merge: true
        )
    }

    func fetchConversations(uid: String, limit: Int = 100) async throws -> [ChatConversationRecord] {
        let snapshot = try await db.collection(FirestoreCollection.chats.rawValue)
            .whereField("participants", arrayContains: uid)
            .limit(to: limit)
            .getDocuments()

        var records: [ChatConversationRecord] = []
        for doc in snapshot.documents {
            guard let parsed = parseConversation(doc) else { continue }
            records.append(try await enrichConversation(parsed, currentUid: uid))
        }

        return records.sorted {
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
        var blindDateInvites: QuerySnapshot?
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

        do {
            blindDateInvites = try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.blindDateInvitations.rawValue)
                .limit(to: limit)
                .getDocuments()
        } catch {
            lastError = error
        }

        if userInvites == nil, chatInvites == nil, blindDateInvites == nil, let lastError {
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

        for doc in blindDateInvites?.documents ?? [] {
            guard let invite = parseBlindDateInvitation(doc) else { continue }
            let key = "blind_date:\(invite.id ?? doc.documentID):\(invite.senderId ?? "")"
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
        let source = (invitation.source ?? "chat").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let invitationId = invitation.id?.trimmingCharacters(in: .whitespacesAndNewlines) ?? senderId
        guard !senderId.isEmpty else {
            throw NSError(
                domain: "ChatRepository",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Invitation sender is missing."]
            )
        }

        if source == "blind_date" {
            let result = try await FunctionsService.shared.call(
                function: .acceptBlindDateInvitation,
                data: ["senderId": senderId]
            )
            try await markInvitationAccepted(uid: uid, invitation: invitation, invitationId: invitationId)
            if let chatId = parseChatId(from: result), !chatId.isEmpty {
                return chatId
            }
            if let existingConversationId = try await findExistingConversation(currentUid: uid, otherUid: senderId) {
                return existingConversationId
            }
            return ""
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
        let source = (invitation.source ?? "chat").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let invitationId = invitation.id?.trimmingCharacters(in: .whitespacesAndNewlines) ?? senderId
        guard !invitationId.isEmpty else {
            throw NSError(
                domain: "ChatRepository",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Invitation reference is missing."]
            )
        }

        if source == "legacy_chat" {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.chatInvitations.rawValue)
                .document(invitationId)
                .delete()
        } else if source == "blind_date" {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.blindDateInvitations.rawValue)
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

    func initiateCall(
        conversationId: String,
        callerUid: String,
        receiverUid: String,
        callType: String
    ) async throws {
        let cleanConversationId = conversationId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCallerUid = callerUid.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanReceiverUid = receiverUid.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedCallType = callType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        guard !cleanConversationId.isEmpty else {
            throw NSError(domain: "ChatRepository", code: 3, userInfo: [NSLocalizedDescriptionKey: "Conversation id is missing."])
        }
        guard !cleanCallerUid.isEmpty, !cleanReceiverUid.isEmpty else {
            throw NSError(domain: "ChatRepository", code: 4, userInfo: [NSLocalizedDescriptionKey: "Call participants are missing."])
        }
        let finalCallType = normalizedCallType == "video" ? "video" : "audio"

        let caller = try await fetchUserSummary(uid: cleanCallerUid)
        let sessionRef = db.collection(FirestoreCollection.callSessions.rawValue).document()
        let callLogRef = db.collection(FirestoreCollection.chats.rawValue)
            .document(cleanConversationId)
            .collection(FirestoreSubcollection.callLogs.rawValue)
            .document(sessionRef.documentID)
        let chatRef = db.collection(FirestoreCollection.chats.rawValue).document(cleanConversationId)

        let batch = db.batch()
        batch.setData(
            [
                "chatId": cleanConversationId,
                "callerId": cleanCallerUid,
                "receiverId": cleanReceiverUid,
                "callerName": caller.name,
                "callerPhotoUrl": caller.photoUrl as Any,
                "callType": finalCallType,
                "status": "ringing",
                "createdAt": FieldValue.serverTimestamp(),
                "updatedAt": FieldValue.serverTimestamp()
            ],
            forDocument: sessionRef,
            merge: true
        )
        batch.setData(
            [
                "chatId": cleanConversationId,
                "callerId": cleanCallerUid,
                "receiverId": cleanReceiverUid,
                "callType": finalCallType,
                "status": "ringing",
                "startedAt": FieldValue.serverTimestamp()
            ],
            forDocument: callLogRef,
            merge: true
        )
        batch.setData(
            [
                "lastCallType": finalCallType,
                "lastCallStatus": "ringing",
                "lastCallTimestamp": FieldValue.serverTimestamp(),
                "lastCallInitiatorId": cleanCallerUid,
                "lastCallReceiverId": cleanReceiverUid
            ],
            forDocument: chatRef,
            merge: true
        )
        try await batch.commit()
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
        let source = (invitation.source ?? "").lowercased()
        if source == "legacy_chat" {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.chatInvitations.rawValue)
                .document(invitationId)
                .delete()
        } else if source == "blind_date" {
            try await db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.blindDateInvitations.rawValue)
                .document(invitationId)
                .setData(
                    [
                        "status": "accepted",
                        "updatedAt": FieldValue.serverTimestamp(),
                        "respondedAt": FieldValue.serverTimestamp()
                    ],
                    merge: true
                )
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
        let source = (invitation.source ?? "").lowercased()
        if source == "legacy_chat" {
            let legacyRef = db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.chatInvitations.rawValue)
                .document(invitationId)
            batch.deleteDocument(legacyRef)
        } else if source == "blind_date" {
            let inviteRef = db.collection(FirestoreCollection.users.rawValue)
                .document(uid)
                .collection(FirestoreSubcollection.blindDateInvitations.rawValue)
                .document(invitationId)
            batch.setData(
                [
                    "status": "accepted",
                    "updatedAt": FieldValue.serverTimestamp(),
                    "respondedAt": FieldValue.serverTimestamp()
                ],
                forDocument: inviteRef,
                merge: true
            )
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
        let lastCallTimestamp = data.firstTimestamp(keys: ["lastCallTimestamp"])
        let participants = data["participants"] as? [String]
        return ChatConversationRecord(
            id: doc.documentID,
            participants: participants,
            otherParticipantId: data.firstNonEmptyString(keys: ["otherParticipantId", "otherUserId"]),
            otherParticipantName: data.firstNonEmptyString(keys: ["otherParticipantName", "otherUserName"]),
            otherParticipantProfilePicUrl: data.firstNonEmptyString(keys: ["otherParticipantProfilePicUrl", "otherParticipantProfileImageUrl"]),
            lastMessage: lastMessage,
            lastMessageText: lastMessage,
            lastMessageTimestamp: lastMessageTimestamp,
            lastCallType: data.firstNonEmptyString(keys: ["lastCallType"]),
            lastCallStatus: data.firstNonEmptyString(keys: ["lastCallStatus"]),
            lastCallTimestamp: lastCallTimestamp,
            lastCallInitiatorId: data.firstNonEmptyString(keys: ["lastCallInitiatorId"]),
            lastCallReceiverId: data.firstNonEmptyString(keys: ["lastCallReceiverId"])
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

    private func parseBlindDateInvitation(_ doc: QueryDocumentSnapshot) -> UserInvitationRecord? {
        let data = doc.data()
        let senderId = data.firstNonEmptyString(keys: ["senderId", "fromUid", "inviterId"]) ?? doc.documentID
        let senderName = data.firstNonEmptyString(keys: ["senderName", "inviterName", "senderDisplayName"]) ?? "Someone"
        let profileImage = data.firstNonEmptyString(keys: ["senderProfilePictureUrl", "senderProfileImageUrl", "senderAvatarUrl"])
        let timestamp = data.firstTimestamp(keys: ["sentAt", "timestamp", "createdAt", "updatedAt"])
        return UserInvitationRecord(
            id: doc.documentID,
            senderId: senderId,
            senderName: senderName,
            inviterName: senderName,
            senderEmail: data.firstNonEmptyString(keys: ["senderEmail", "email"]),
            senderProfileImageUrl: profileImage,
            status: data.firstNonEmptyString(keys: ["status"]) ?? "pending",
            source: "blind_date",
            context: "Blind Date",
            timestamp: timestamp
        )
    }

    private func enrichConversation(
        _ conversation: ChatConversationRecord,
        currentUid: String
    ) async throws -> ChatConversationRecord {
        let participants = conversation.participants ?? []
        let otherId = conversation.otherParticipantId
            ?? participants.first(where: { $0 != currentUid })

        guard let otherId, !otherId.isEmpty else { return conversation }
        let summary = try await fetchUserSummary(uid: otherId)
        return ChatConversationRecord(
            id: conversation.id,
            participants: conversation.participants,
            otherParticipantId: otherId,
            otherParticipantName: conversation.otherParticipantName ?? summary.name,
            otherParticipantProfilePicUrl: conversation.otherParticipantProfilePicUrl ?? summary.photoUrl,
            lastMessage: conversation.lastMessage,
            lastMessageText: conversation.lastMessageText,
            lastMessageTimestamp: conversation.lastMessageTimestamp,
            lastCallType: conversation.lastCallType,
            lastCallStatus: conversation.lastCallStatus,
            lastCallTimestamp: conversation.lastCallTimestamp,
            lastCallInitiatorId: conversation.lastCallInitiatorId,
            lastCallReceiverId: conversation.lastCallReceiverId
        )
    }

    private func fetchUserSummary(uid: String) async throws -> (name: String, photoUrl: String?) {
        let cleanUid = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        if let cached = userSummaryCache[cleanUid] {
            return cached
        }

        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .document(cleanUid)
            .getDocument()
        let data = snapshot.data() ?? [:]
        let name = data.firstNonEmptyString(keys: ["name", "username", "email"]) ?? "Unknown user"
        let photoUrl = data.firstNonEmptyString(keys: ["profilePictureUrl", "profileImageUrl", "avatarUrl"])
        let summary = (name: name, photoUrl: photoUrl)
        userSummaryCache[cleanUid] = summary
        return summary
    }

    private func parseChatId(from functionResult: Any?) -> String? {
        if let raw = functionResult as? String {
            let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            return clean.isEmpty ? nil : clean
        }
        if let map = functionResult as? [String: Any] {
            if let chatId = (map["chatId"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
               !chatId.isEmpty {
                return chatId
            }
            if let data = map["data"] as? [String: Any] {
                return parseChatId(from: data)
            }
            if let result = map["result"] as? [String: Any] {
                return parseChatId(from: result)
            }
        }
        return nil
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
