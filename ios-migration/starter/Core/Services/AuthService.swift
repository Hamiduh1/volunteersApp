import Foundation
import FirebaseAuth
import FirebaseFirestore

final class AuthService {
    static let shared = AuthService()
    private init() {}

    var currentUser: User? {
        Auth.auth().currentUser
    }

    func observeAuthState(_ onChange: @escaping (User?) -> Void) -> AuthStateDidChangeListenerHandle {
        Auth.auth().addStateDidChangeListener { _, user in
            onChange(user)
        }
    }

    func removeObserver(_ handle: AuthStateDidChangeListenerHandle) {
        Auth.auth().removeStateDidChangeListener(handle)
    }

    func signIn(email: String, password: String) async throws -> User {
        let result = try await Auth.auth().signIn(withEmail: email, password: password)
        return result.user
    }

    func signUp(
        email: String,
        password: String,
        name: String,
        username: String,
        role: AppUserRole = .volunteer
    ) async throws -> User {
        let result = try await Auth.auth().createUser(withEmail: email, password: password)
        let user = result.user

        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let emailPrefix = cleanEmail.split(separator: "@").first.map(String.init) ?? "user"
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)

        let userPayload: [String: Any] = [
            "uid": user.uid,
            "email": cleanEmail,
            "name": cleanName.isEmpty ? emailPrefix : cleanName,
            "username": cleanUsername.isEmpty ? emailPrefix : cleanUsername,
            "role": role.rawValue,
            "userRole": role.rawValue,
            "emailVerified": user.isEmailVerified,
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ]

        try await Firestore.firestore()
            .collection(FirestoreCollection.users.rawValue)
            .document(user.uid)
            .setData(userPayload, merge: true)

        return user
    }

    func signOut() throws {
        try Auth.auth().signOut()
    }

    func sendPasswordReset(email: String) async throws {
        try await withCheckedThrowingContinuation { continuation in
            Auth.auth().sendPasswordReset(withEmail: email) { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
    }
}
