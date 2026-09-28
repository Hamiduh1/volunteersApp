import Foundation
import Combine

@MainActor
final class OrganizerProfileSetupViewModel: ObservableObject {
    @Published var name = ""
    @Published var email = ""
    @Published var organizationName = ""
    @Published var bio = ""
    @Published var location = ""
    @Published var profileImageUrl: String?
    @Published var isLoading = false
    @Published var isSaving = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OrganizerRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let profile = try await repository.fetchOrganizerProfileSetup(uid: uid)
            name = profile.name
            email = profile.email
            organizationName = profile.organizationName
            bio = profile.bio
            location = profile.location
            profileImageUrl = profile.profileImageUrl
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func save(uid: String) async {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanOrg = organizationName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else {
            errorMessage = "Name is required."
            return
        }
        guard !cleanOrg.isEmpty else {
            errorMessage = "Organization name is required."
            return
        }

        isSaving = true
        errorMessage = nil
        statusMessage = nil
        defer { isSaving = false }

        do {
            try await repository.saveOrganizerProfileSetup(
                uid: uid,
                name: cleanName,
                organizationName: cleanOrg,
                bio: bio,
                location: location
            )
            statusMessage = "Organizer profile saved."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

