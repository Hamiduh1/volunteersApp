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
        phoneNumber: String? = nil,
        country: String? = nil,
        companyName: String? = nil,
        birthDate: Date? = nil,
        role: AppUserRole = .volunteer
    ) async throws -> User {
        let result = try await Auth.auth().createUser(withEmail: email, password: password)
        let user = result.user

        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let emailPrefix = cleanEmail.split(separator: "@").first.map(String.init) ?? "user"
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPhone = phoneNumber?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let cleanCountry = country?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let cleanCompany = companyName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let normalizedRole = role.rawValue

        var userPayload: [String: Any] = [
            "uid": user.uid,
            "email": cleanEmail,
            "name": cleanName.isEmpty ? emailPrefix : cleanName,
            "username": cleanUsername.isEmpty ? emailPrefix : cleanUsername,
            "phoneNumber": cleanPhone,
            "country": cleanCountry,
            "userType": normalizedRole,
            "role": normalizedRole,
            "userRole": normalizedRole,
            "profileStatus": "active",
            "emailVerified": user.isEmailVerified,
            "wallet": ["balance": 0],
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ]

        if normalizedRole == AppUserRole.organizer.rawValue || normalizedRole == AppUserRole.employer.rawValue {
            userPayload["organizationName"] = cleanCompany
            userPayload["companyName"] = cleanCompany
            userPayload["businessRegistered"] = false
        } else if normalizedRole == AppUserRole.volunteer.rawValue, let birthDate {
            let cutoffDate = Calendar.current.date(byAdding: .year, value: -18, to: Date()) ?? Date()
            let isMinor = birthDate > cutoffDate
            userPayload["birthDate"] = Timestamp(date: birthDate)
            userPayload["isMinor"] = isMinor
            userPayload["requiresParentalConsent"] = isMinor
            userPayload["canAccessDating"] = !isMinor
        }

        try await Firestore.firestore()
            .collection(FirestoreCollection.users.rawValue)
            .document(user.uid)
            .setData(userPayload, merge: true)

        do {
            _ = try await requestEmailVerificationCode()
        } catch {
            print("AuthService: failed to request initial verification code: \(error.localizedDescription)")
        }

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

    func requestEmailVerificationCode() async throws -> Int {
        let response = try await FunctionsService.shared.callMap(function: .requestEmailVerificationCode)
        return (response["cooldownSeconds"] as? NSNumber)?.intValue ?? 120
    }

    func verifyEmailCode(email: String, code: String) async throws {
        _ = try await FunctionsService.shared.call(
            function: .verifyEmailVerificationCode,
            data: [
                "email": email,
                "code": code
            ]
        )
    }
}
