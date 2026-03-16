import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift
import FirebaseStorage

final class GalleryRepository {
    private let db = Firestore.firestore()
    private let storage = Storage.storage()

    func fetchUploads(limit: Int = 200) async throws -> [GalleryUploadRecord] {
        let snapshot = try await db.collection(FirestoreCollection.galleryUploads.rawValue)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.compactMap { try? $0.data(as: GalleryUploadRecord.self) }
    }

    func uploadImage(
        user: AppSessionUser,
        eventName: String,
        imageData: Data,
        eventId: String? = nil
    ) async throws {
        let cleanName = eventName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else {
            throw NSError(
                domain: "GalleryRepository",
                code: 8101,
                userInfo: [NSLocalizedDescriptionKey: "Event name is required."]
            )
        }

        let safeName = cleanName
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "/", with: "_")
        let fileName = "\(safeName)_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
        let storagePath = "\(StorageFolder.galleryUploads.rawValue)/\(user.uid)/\(fileName)"

        let fileRef = storage.reference().child(storagePath)
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"

        _ = try await withCheckedThrowingContinuation { continuation in
            fileRef.putData(imageData, metadata: metadata) { result, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: result)
                }
            }
        }

        let downloadUrl = try await withCheckedThrowingContinuation { continuation in
            fileRef.downloadURL { url, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let url {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(
                        throwing: NSError(
                            domain: "GalleryRepository",
                            code: 8102,
                            userInfo: [NSLocalizedDescriptionKey: "Unable to resolve uploaded image URL."]
                        )
                    )
                }
            }
        }

        let docRef = db.collection(FirestoreCollection.galleryUploads.rawValue).document()
        try await docRef.setData(
            [
                "name": cleanName,
                "imageUrl": downloadUrl.absoluteString,
                "imagePathInStorage": storagePath,
                "uploaderId": user.uid,
                "eventId": eventId as Any,
                "timestamp": FieldValue.serverTimestamp()
            ],
            merge: true
        )
    }
}
