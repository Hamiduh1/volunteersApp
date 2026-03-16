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
        for doc in userInvites.documents + chatInvites.documents {
            if let invite = try? doc.data(as: UserInvitationRecord.self) {
                merged[doc.reference.path] = invite
            }
        }

        return merged.values.sorted {
            let l = $0.timestamp?.dateValue() ?? .distantPast
            let r = $1.timestamp?.dateValue() ?? .distantPast
            return l > r
        }
    }
}
