import Foundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import FirebaseMessaging

/// Registers the FCM token on `users/{uid}.fcmToken` so Android→iOS calls can ring via Cloud Functions.
enum PushTokenRegistrar {
    static func registerCurrentToken() {
        Messaging.messaging().token { token, error in
            if let error {
                print("PushTokenRegistrar: failed to fetch FCM token: \(error.localizedDescription)")
                return
            }
            guard let token, !token.isEmpty else { return }
            Task { await register(token: token) }
        }
    }

    static func register(token: String) async {
        let clean = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, let uid = Auth.auth().currentUser?.uid, !uid.isEmpty else { return }

        do {
            let callable = Functions.functions(region: "us-central1").httpsCallable("registerUserPushTokens")
            _ = try await callable.call([
                "fcmToken": clean,
                "platform": "ios",
            ])
        } catch {
            // Fallback if callable is unavailable / App Check fails — still write fcmToken for call push.
            do {
                try await Firestore.firestore().collection("users").document(uid).setData([
                    "fcmToken": clean,
                    "fcmTokens": FieldValue.arrayUnion([clean]),
                    "pushPlatform": "ios",
                    "updatedAt": FieldValue.serverTimestamp(),
                ], merge: true)
            } catch {
                print("PushTokenRegistrar: Firestore fallback failed: \(error.localizedDescription)")
            }
        }
    }
}
