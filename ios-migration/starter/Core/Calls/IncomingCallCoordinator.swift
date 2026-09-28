import Combine
import FirebaseAuth
import FirebaseFirestore
import Foundation

struct IncomingCallSession: Identifiable, Equatable {
    let id: String
    let chatId: String
    let callerId: String
    let receiverId: String
    let participantIds: [String]
    let acceptedParticipantIds: [String]
    let declinedParticipantIds: [String]
    let endedParticipantIds: [String]
    let callerName: String
    let callType: String
    let status: String
    let isGroup: Bool
    let agoraChannelName: String
    let createdAt: Date?

    init?(document: DocumentSnapshot) {
        guard document.exists else { return nil }
        let data = document.data() ?? [:]
        let chatId = (data["chatId"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let callerId = (data["callerId"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !chatId.isEmpty, !callerId.isEmpty else { return nil }

        func ids(_ key: String) -> [String] {
            (data[key] as? [String] ?? [])
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        }

        let callType = ((data["callType"] as? String) ?? "audio")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        self.id = document.documentID
        self.chatId = chatId
        self.callerId = callerId
        self.receiverId = (data["receiverId"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.participantIds = ids("participantIds")
        self.acceptedParticipantIds = ids("acceptedParticipantIds")
        self.declinedParticipantIds = ids("declinedParticipantIds")
        self.endedParticipantIds = ids("endedParticipantIds")
        self.callerName = (data["callerName"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Incoming call"
        self.callType = callType == "video" ? "video" : "audio"
        self.status = ((data["status"] as? String) ?? "ringing").lowercased()
        self.isGroup = (data["isGroup"] as? Bool == true) || self.participantIds.count > 2
        self.agoraChannelName = (data["agoraChannelName"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? "call_\(document.documentID)"
        self.createdAt = (data["createdAt"] as? Timestamp)?.dateValue()
    }

    var isVideo: Bool { callType == "video" }
    var title: String {
        switch (isGroup, isVideo) {
        case (true, true): return "Incoming group video call"
        case (true, false): return "Incoming group voice call"
        case (false, true): return "Incoming video call"
        case (false, false): return "Incoming voice call"
        }
    }
}

@MainActor
final class IncomingCallCoordinator: ObservableObject {
    @Published private(set) var ringingSession: IncomingCallSession?
    @Published private(set) var activeSession: IncomingCallSession?
    @Published private(set) var errorMessage: String?

    private let db = Firestore.firestore()
    private var authListener: AuthStateDidChangeListenerHandle?
    private var mirrorListener: ListenerRegistration?
    private var directFallbackListener: ListenerRegistration?
    private var groupFallbackListener: ListenerRegistration?
    private var sessions: [String: IncomingCallSession] = [:]
    private var boundUid: String?

    init() {
        authListener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                self?.bind(to: user?.uid)
            }
        }
        bind(to: Auth.auth().currentUser?.uid)
    }

    deinit {
        if let authListener {
            Auth.auth().removeStateDidChangeListener(authListener)
        }
        removeListeners()
    }

    func handleNotificationPayload(_ userInfo: [AnyHashable: Any]) {
        let rawId = userInfo["sessionId"] ?? userInfo["callId"] ?? userInfo["callSessionId"]
        guard let sessionId = (rawId as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !sessionId.isEmpty else {
            return
        }
        let type = (userInfo["type"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if type == "call_cancelled" {
            sessions.removeValue(forKey: sessionId)
            if ringingSession?.id == sessionId { ringingSession = nil }
            if activeSession?.id == sessionId { activeSession = nil }
            return
        }
        Task { await loadSession(id: sessionId) }
    }

    func acceptRingingCall() async throws -> IncomingCallSession {
        guard let session = ringingSession, let uid = Auth.auth().currentUser?.uid, !uid.isEmpty else {
            throw CallSessionError.missingSession
        }
        var updates: [String: Any] = [
            "acceptedParticipantIds": FieldValue.arrayUnion([uid]),
            "declinedParticipantIds": FieldValue.arrayRemove([uid]),
            "endedParticipantIds": FieldValue.arrayRemove([uid]),
            "updatedAt": FieldValue.serverTimestamp(),
        ]
        if !session.isGroup {
            updates["status"] = "accepted"
        }
        try await db.collection(FirestoreCollection.callSessions.rawValue)
            .document(session.id)
            .updateData(updates)
        sessions.removeValue(forKey: session.id)
        ringingSession = nil
        activeSession = session
        return session
    }

    /// Activates a caller-owned session after the shared callable has created it.
    func activateOutgoingCall(id: String) async throws -> IncomingCallSession {
        let cleanId = id.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty, let uid = Auth.auth().currentUser?.uid, !uid.isEmpty else {
            throw CallSessionError.missingSession
        }

        let document = try await db.collection(FirestoreCollection.callSessions.rawValue)
            .document(cleanId)
            .getDocument()
        guard let session = IncomingCallSession(document: document), session.callerId == uid else {
            throw NSError(
                domain: "IncomingCallCoordinator",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "The new call session could not be opened."]
            )
        }

        sessions.removeValue(forKey: session.id)
        ringingSession = nil
        activeSession = session
        return session
    }

    func declineRingingCall() async {
        guard let session = ringingSession, let uid = Auth.auth().currentUser?.uid, !uid.isEmpty else { return }
        do {
            var updates: [String: Any] = [
                "declinedParticipantIds": FieldValue.arrayUnion([uid]),
                "acceptedParticipantIds": FieldValue.arrayRemove([uid]),
                "endedParticipantIds": FieldValue.arrayRemove([uid]),
                "updatedAt": FieldValue.serverTimestamp(),
            ]
            if !session.isGroup {
                updates["status"] = "declined"
            }
            try await db.collection(FirestoreCollection.callSessions.rawValue)
                .document(session.id)
                .updateData(updates)
            if !session.isGroup {
                try? await db.collection(FirestoreCollection.chats.rawValue)
                    .document(session.chatId)
                    .collection(FirestoreSubcollection.callLogs.rawValue)
                    .document(session.id)
                    .setData([
                        "status": "missed",
                        "endedAt": FieldValue.serverTimestamp(),
                    ], merge: true)
            }
        } catch {
            errorMessage = "Could not decline this call. Please try again."
        }
        sessions.removeValue(forKey: session.id)
        ringingSession = nil
    }

    func endActiveCall(completed: Bool = false) async {
        guard let session = activeSession, let uid = Auth.auth().currentUser?.uid, !uid.isEmpty else {
            activeSession = nil
            return
        }
        do {
            var updates: [String: Any] = [
                "endedParticipantIds": FieldValue.arrayUnion([uid]),
                "updatedAt": FieldValue.serverTimestamp(),
            ]
            if !session.isGroup || session.callerId == uid {
                updates["status"] = "ended"
            }
            try await db.collection(FirestoreCollection.callSessions.rawValue)
                .document(session.id)
                .updateData(updates)
            if !session.isGroup {
                try? await db.collection(FirestoreCollection.chats.rawValue)
                    .document(session.chatId)
                    .collection(FirestoreSubcollection.callLogs.rawValue)
                    .document(session.id)
                    .setData([
                        "status": completed ? "completed" : "missed",
                        "endedAt": FieldValue.serverTimestamp(),
                    ], merge: true)
            }
        } catch {
            errorMessage = "Could not end this call on all devices."
        }
        activeSession = nil
    }

    func clearError() {
        errorMessage = nil
    }

    func reportError(_ message: String) {
        errorMessage = message
    }

    private func bind(to uid: String?) {
        let cleanUid = uid?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !cleanUid.isEmpty else {
            boundUid = nil
            sessions.removeAll()
            ringingSession = nil
            activeSession = nil
            removeListeners()
            return
        }
        if let boundUid, boundUid == cleanUid, mirrorListener != nil {
            return
        }
        boundUid = cleanUid
        sessions.removeAll()
        ringingSession = nil
        removeListeners()
        attachMirrorListener(uid: cleanUid)
    }

    private func attachMirrorListener(uid: String) {
        mirrorListener = db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .collection(FirestoreSubcollection.incomingCallSessions.rawValue)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    if error != nil {
                        self.attachLegacyFallbackListeners(uid: uid)
                        return
                    }
                    self.sessions = Dictionary(
                        uniqueKeysWithValues: (snapshot?.documents ?? []).compactMap { document in
                            IncomingCallSession(document: document).map { ($0.id, $0) }
                        }
                    )
                    self.presentFreshRingingCall(for: uid)
                }
            }
    }

    private func attachLegacyFallbackListeners(uid: String) {
        guard directFallbackListener == nil, groupFallbackListener == nil else { return }
        directFallbackListener = db.collection(FirestoreCollection.callSessions.rawValue)
            .whereField("receiverId", isEqualTo: uid)
            .whereField("status", isEqualTo: "ringing")
            .addSnapshotListener { [weak self] snapshot, _ in
                Task { @MainActor in
                    self?.mergeLegacySessions(snapshot?.documents ?? [], uid: uid)
                }
            }
        groupFallbackListener = db.collection(FirestoreCollection.callSessions.rawValue)
            .whereField("participantIds", arrayContains: uid)
            .whereField("status", isEqualTo: "ringing")
            .addSnapshotListener { [weak self] snapshot, _ in
                Task { @MainActor in
                    self?.mergeLegacySessions(snapshot?.documents ?? [], uid: uid)
                }
            }
    }

    private func mergeLegacySessions(_ documents: [QueryDocumentSnapshot], uid: String) {
        documents.compactMap(IncomingCallSession.init(document:)).forEach { sessions[$0.id] = $0 }
        presentFreshRingingCall(for: uid)
    }

    private func presentFreshRingingCall(for uid: String) {
        let candidate = sessions.values
            .filter { $0.status == "ringing" && $0.callerId != uid }
            .filter { !$0.acceptedParticipantIds.contains(uid) }
            .filter { !$0.declinedParticipantIds.contains(uid) }
            .filter { !$0.endedParticipantIds.contains(uid) }
            .sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
            .first
        ringingSession = candidate
    }

    private func loadSession(id: String) async {
        do {
            let document = try await db.collection(FirestoreCollection.callSessions.rawValue)
                .document(id)
                .getDocument()
            guard let session = IncomingCallSession(document: document),
                  let uid = Auth.auth().currentUser?.uid,
                  session.status == "ringing",
                  session.callerId != uid else { return }
            sessions[session.id] = session
            presentFreshRingingCall(for: uid)
        } catch {
            errorMessage = "Could not open the incoming call."
        }
    }

    private func removeListeners() {
        mirrorListener?.remove()
        directFallbackListener?.remove()
        groupFallbackListener?.remove()
        mirrorListener = nil
        directFallbackListener = nil
        groupFallbackListener = nil
    }
}

enum CallSessionError: LocalizedError {
    case missingSession

    var errorDescription: String? {
        switch self {
        case .missingSession: return "This incoming call is no longer available."
        }
    }
}
