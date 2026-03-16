import Foundation
import Combine
import FirebaseAuth
import FirebaseFirestore

struct AppSessionUser {
    let uid: String
    let email: String?
    let role: AppUserRole
}

@MainActor
final class SessionManager: ObservableObject {
    enum SessionState {
        case loading
        case signedOut
        case signedIn(AppSessionUser)
        case error(String)
    }

    @Published private(set) var state: SessionState = .loading
    private var authHandle: AuthStateDidChangeListenerHandle?

    init() {
        start()
    }

    deinit {
        if let authHandle {
            AuthService.shared.removeObserver(authHandle)
        }
    }

    func start() {
        guard authHandle == nil else { return }
        authHandle = AuthService.shared.observeAuthState { [weak self] user in
            guard let self else { return }
            Task { await self.handleAuthChange(user) }
        }
    }

    private func handleAuthChange(_ user: User?) async {
        guard let user else {
            state = .signedOut
            return
        }

        state = .loading
        do {
            let role = try await resolveRole(uid: user.uid)
            state = .signedIn(
                AppSessionUser(uid: user.uid, email: user.email, role: role)
            )
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    private func resolveRole(uid: String) async throws -> AppUserRole {
        let db = Firestore.firestore()

        let userDoc = try await db.collection("users").document(uid).getDocument()
        if let data = userDoc.data() {
            let roleString = (data["role"] as? String) ?? (data["userRole"] as? String)
            let parsed = AppUserRole(rawRole: roleString)
            if parsed != .unknown { return parsed }
        }

        let organizerDoc = try await db.collection("organizers").document(uid).getDocument()
        if organizerDoc.exists { return .organizer }

        let employerDoc = try await db.collection("employers").document(uid).getDocument()
        if employerDoc.exists { return .employer }

        return .volunteer
    }
}
