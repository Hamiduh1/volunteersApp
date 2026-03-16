import Foundation
import FirebaseFirestore

final class FirestoreService {
    static let shared = FirestoreService()
    private init() {}

    let db = Firestore.firestore()

    func document(collection: FirestoreCollection, id: String) -> DocumentReference {
        db.collection(collection.rawValue).document(id)
    }

    func collection(_ collection: FirestoreCollection) -> CollectionReference {
        db.collection(collection.rawValue)
    }
}
