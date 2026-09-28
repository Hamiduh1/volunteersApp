import Foundation
import Combine

@MainActor
final class EmployerProfileSetupViewModel: ObservableObject {
    @Published var name = ""
    @Published var email = ""
    @Published var organizationName = ""
    @Published var contactEmail = ""
    @Published var description = ""
    @Published var profileImageUrl: String?
    @Published var isLoading = false
    @Published var isSaving = false
    @Published var isUploadingImage = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = EmployerRepository()
    private let profileRepository = ProfileRepository()

    func refresh(uid: String) async {
        isLoading = true
        statusMessage = nil
        errorMessage = nil
        defer { isLoading = false }

        do {
            let profile = try await repository.fetchEmployerProfileSetup(uid: uid)
            name = profile.name
            email = profile.email
            organizationName = profile.organizationName
            contactEmail = profile.contactEmail
            description = profile.description
            profileImageUrl = profile.profileImageUrl
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func save(uid: String) async {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanOrganization = organizationName.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanContactEmail = contactEmail.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanName.isEmpty else {
            errorMessage = "Name is required."
            return
        }
        guard !cleanOrganization.isEmpty else {
            errorMessage = "Organization name is required."
            return
        }
        guard !cleanContactEmail.isEmpty else {
            errorMessage = "Contact email is required."
            return
        }

        isSaving = true
        errorMessage = nil
        statusMessage = nil
        defer { isSaving = false }

        do {
            try await repository.saveEmployerProfileSetup(
                uid: uid,
                name: cleanName,
                organizationName: cleanOrganization,
                contactEmail: cleanContactEmail,
                description: description
            )
            statusMessage = "Employer profile saved."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func uploadProfileImage(uid: String, jpegData: Data) async {
        isUploadingImage = true
        errorMessage = nil
        defer { isUploadingImage = false }

        do {
            let url = try await profileRepository.uploadProfileImage(uid: uid, jpegData: jpegData)
            try await repository.saveEmployerProfileImage(uid: uid, profileUrl: url)
            profileImageUrl = url
            statusMessage = "Profile image updated."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
