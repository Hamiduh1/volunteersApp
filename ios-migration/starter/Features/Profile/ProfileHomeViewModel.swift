import Foundation
import Combine

@MainActor
final class ProfileHomeViewModel: ObservableObject {
    @Published private(set) var profile = UserProfileRecord(
        uid: "",
        email: "",
        name: "",
        username: "",
        phoneNumber: "",
        profileImageUrl: nil,
        role: "",
        isEmailVerified: false
    )

    @Published var name = ""
    @Published var username = ""
    @Published var phoneNumber = ""
    @Published var profileImageUrl: String?

    @Published var isLoading = false
    @Published var isSaving = false
    @Published var isUploadingImage = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = ProfileRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let fetched = try await repository.fetchProfile(uid: uid)
            profile = fetched
            name = fetched.name
            username = fetched.username
            phoneNumber = fetched.phoneNumber
            profileImageUrl = fetched.profileImageUrl
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func save(uid: String) async {
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            try await repository.saveProfile(
                uid: uid,
                name: name,
                username: username,
                phoneNumber: phoneNumber
            )
            statusMessage = "Profile saved."
            await refresh(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func uploadProfileImage(uid: String, jpegData: Data) async {
        isUploadingImage = true
        errorMessage = nil
        defer { isUploadingImage = false }

        do {
            let url = try await repository.uploadProfileImage(uid: uid, jpegData: jpegData)
            profileImageUrl = url
            statusMessage = "Profile image updated."
            await refresh(uid: uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
