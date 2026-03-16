import Foundation
import FirebaseFirestore
import FirebaseStorage

final class ProfileRepository {
    private let db = Firestore.firestore()
    private let storage = Storage.storage()

    func fetchProfile(uid: String) async throws -> UserProfileRecord {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue).document(uid).getDocument()
        let data = snapshot.data() ?? [:]

        return UserProfileRecord(
            uid: uid,
            email: (data["email"] as? String) ?? "",
            name: (data["name"] as? String) ?? "",
            username: (data["username"] as? String) ?? "",
            phoneNumber: (data["phoneNumber"] as? String) ?? "",
            profileImageUrl: data["profileImageUrl"] as? String,
            role: ((data["role"] as? String) ?? (data["userRole"] as? String) ?? "").lowercased(),
            isEmailVerified: (data["emailVerified"] as? Bool) ?? false
        )
    }

    func saveProfile(
        uid: String,
        name: String,
        username: String,
        phoneNumber: String
    ) async throws {
        try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .setData(
                [
                    "name": name.trimmingCharacters(in: .whitespacesAndNewlines),
                    "username": username.trimmingCharacters(in: .whitespacesAndNewlines),
                    "phoneNumber": phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines),
                    "updatedAt": FieldValue.serverTimestamp()
                ],
                merge: true
            )
    }

    func uploadProfileImage(uid: String, jpegData: Data) async throws -> String {
        let path = "\(StorageFolder.profileImages.rawValue)/\(uid)/\(UUID().uuidString).jpg"
        let ref = storage.reference().child(path)

        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        _ = try await withCheckedThrowingContinuation { continuation in
            ref.putData(jpegData, metadata: metadata) { meta, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: meta)
                }
            }
        }

        let downloadUrl = try await withCheckedThrowingContinuation { continuation in
            ref.downloadURL { url, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let url {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(throwing: NSError(domain: "ProfileRepository", code: -1, userInfo: [NSLocalizedDescriptionKey: "Missing download URL"]))
                }
            }
        }

        let url = downloadUrl.absoluteString
        try await db.collection(FirestoreCollection.users.rawValue)
            .document(uid)
            .setData(
                [
                    "profileImageUrl": url,
                    "updatedAt": FieldValue.serverTimestamp()
                ],
                merge: true
            )
        return url
    }
}
