import Foundation

enum DateGateResult {
    case proceed
    case requireAge
    case requirePreferences
}

enum DateHubTab: String, CaseIterable, Identifiable {
    case datingLoop = "Dating Loop"
    case blindDate = "Blind Date"

    var id: String { rawValue }
}

enum BlindGenderFilter: String, CaseIterable, Identifiable {
    case all = "All"
    case male = "Male"
    case female = "Female"
    case other = "Other"

    var id: String { rawValue }

    var targetRawValue: String? {
        switch self {
        case .all:
            return nil
        case .male:
            return DatingGender.male.rawValue
        case .female:
            return DatingGender.female.rawValue
        case .other:
            return DatingGender.other.rawValue
        }
    }
}

@MainActor
final class DateHubViewModel: ObservableObject {
    @Published var selectedTab: DateHubTab = .datingLoop
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?
    @Published var hasVerifiedAge = false
    @Published var selectedAge: Int = 0

    // Dating Loop state
    @Published var datingProfiles: [DatingProfileRecord] = []
    @Published var myDatingProfile: DatingProfileRecord?
    @Published var profileName = ""
    @Published var profileBio = ""
    @Published var profilePhone = ""
    @Published var profileCountry = Locale.current.localizedString(forRegionCode: Locale.current.region?.identifier ?? "US") ?? "United States"
    @Published var profileGender: DatingGender = .other
    @Published var profileLookingFor: DatingLookingFor = .everyone
    @Published var existingDatingImageUrls: [String] = []

    // Blind Date state
    @Published var blindStatus: BlindDateUserStatusRecord = .notJoined
    @Published var isStaffExempt = false
    @Published var blindProfiles: [BlindDateProfileRecord] = []
    @Published var filteredBlindProfiles: [BlindDateProfileRecord] = []
    @Published var receivedInvitations: [BlindDateInvitationRecord] = []
    @Published var sentInvitations: [BlindDateInvitationRecord] = []
    @Published var invitationTimeline: [BlindDateTimelineItemRecord] = []
    @Published var blindSearchQuery = ""
    @Published var blindGenderFilter: BlindGenderFilter = .all
    @Published var blindBio = ""
    @Published var blindGender: DatingGender = .other
    @Published var matchedChatId: String?

    private let user: AppSessionUser
    private let repository = DateRepository()

    init(user: AppSessionUser) {
        self.user = user
    }

    var canJoinBlindDate: Bool {
        blindStatus == .notJoined || blindStatus == .expired
    }

    var hasDatingProfile: Bool {
        myDatingProfile != nil
    }

    var hasDatingPreferences: Bool {
        hasDatingProfile
    }

    func gateBlindDateFlow() -> DateGateResult {
        if !hasVerifiedAge {
            return .requireAge
        }
        if !hasDatingPreferences {
            return .requirePreferences
        }
        return .proceed
    }

    func verifyAge(_ age: Int) {
        selectedAge = age
        hasVerifiedAge = age >= 18
    }

    func saveDatingPreferences(gender: DatingGender, lookingFor: DatingLookingFor) {
        profileGender = gender
        profileLookingFor = lookingFor
        blindGender = gender
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let (profiles, mine) = try await repository.fetchDatingProfiles(currentUid: user.uid)
            datingProfiles = profiles
            myDatingProfile = mine
            if let mine {
                applyMyProfileToForm(mine)
            } else {
                hasVerifiedAge = false
            }

            let overview = try await repository.fetchBlindDateOverview(uid: user.uid)
            blindStatus = overview.status
            isStaffExempt = overview.isStaffExempt
            blindProfiles = overview.profiles
            receivedInvitations = overview.receivedInvitations
            sentInvitations = overview.sentInvitations
            invitationTimeline = overview.invitationTimeline
            applyBlindFilters()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func saveDatingProfile(newImageData: [Data]) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            try await repository.saveDatingProfile(
                uid: user.uid,
                name: profileName,
                bio: profileBio,
                gender: profileGender,
                lookingFor: profileLookingFor,
                phone: profilePhone,
                country: profileCountry,
                existingImageUrls: existingDatingImageUrls,
                newImageData: newImageData
            )
            statusMessage = "Dating profile saved."
            let (profiles, mine) = try await repository.fetchDatingProfiles(currentUid: user.uid)
            datingProfiles = profiles
            myDatingProfile = mine
            applyMyProfileToForm(mine)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func deleteDatingProfile() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            try await repository.deleteDatingProfile(uid: user.uid)
            myDatingProfile = nil
            hasVerifiedAge = false
            profileName = ""
            profileBio = ""
            profilePhone = ""
            existingDatingImageUrls = []
            statusMessage = "Dating profile deleted."
            let (profiles, _) = try await repository.fetchDatingProfiles(currentUid: user.uid)
            datingProfiles = profiles
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func joinBlindDate(newMediaData: [DateMediaUpload]) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let charged = try await repository.joinBlindDate(
                uid: user.uid,
                mediaData: newMediaData,
                bio: blindBio,
                gender: blindGender
            )
            statusMessage = charged
                ? "Payment successful. You joined Blind Date."
                : "Staff access confirmed. You joined Blind Date with no fee."
            let overview = try await repository.fetchBlindDateOverview(uid: user.uid)
            blindStatus = overview.status
            isStaffExempt = overview.isStaffExempt
            blindProfiles = overview.profiles
            receivedInvitations = overview.receivedInvitations
            sentInvitations = overview.sentInvitations
            invitationTimeline = overview.invitationTimeline
            applyBlindFilters()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func rejoinBlindDate() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let charged = try await repository.rejoinBlindDate()
            statusMessage = charged
                ? "You are back in the Blind Date loop."
                : "Staff access confirmed. Rejoined with no fee."
            let overview = try await repository.fetchBlindDateOverview(uid: user.uid)
            blindStatus = overview.status
            isStaffExempt = overview.isStaffExempt
            blindProfiles = overview.profiles
            receivedInvitations = overview.receivedInvitations
            sentInvitations = overview.sentInvitations
            invitationTimeline = overview.invitationTimeline
            applyBlindFilters()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func sendBlindInvitation(to recipientId: String) async {
        do {
            try await repository.sendBlindDateInvitation(currentUid: user.uid, recipientId: recipientId)
            statusMessage = "Invitation sent. Waiting for response."
            let overview = try await repository.fetchBlindDateOverview(uid: user.uid)
            receivedInvitations = overview.receivedInvitations
            sentInvitations = overview.sentInvitations
            invitationTimeline = overview.invitationTimeline
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func sendDatingChatInvitation(to recipientId: String) async {
        do {
            try await repository.sendDatingChatInvitation(currentUid: user.uid, recipientId: recipientId)
            statusMessage = "Chat invitation sent."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func acceptBlindInvitation(_ invitation: BlindDateInvitationRecord) async {
        do {
            let chatId = try await repository.acceptBlindDateInvitation(senderId: invitation.senderId)
            matchedChatId = chatId
            statusMessage = "Invitation accepted. Chat is ready."
            let overview = try await repository.fetchBlindDateOverview(uid: user.uid)
            blindStatus = overview.status
            receivedInvitations = overview.receivedInvitations
            sentInvitations = overview.sentInvitations
            invitationTimeline = overview.invitationTimeline
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func declineBlindInvitation(_ invitation: BlindDateInvitationRecord) async {
        do {
            try await repository.declineBlindDateInvitation(senderId: invitation.senderId)
            statusMessage = "Invitation declined."
            let overview = try await repository.fetchBlindDateOverview(uid: user.uid)
            receivedInvitations = overview.receivedInvitations
            sentInvitations = overview.sentInvitations
            invitationTimeline = overview.invitationTimeline
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func updateBlindSearchQuery(_ value: String) {
        blindSearchQuery = value
        applyBlindFilters()
    }

    func updateBlindGenderFilter(_ value: BlindGenderFilter) {
        blindGenderFilter = value
        applyBlindFilters()
    }

    private func applyMyProfileToForm(_ profile: DatingProfileRecord?) {
        guard let profile else {
            return
        }
        hasVerifiedAge = true
        profileName = profile.name
        profileBio = profile.bio
        profilePhone = profile.phone
        profileCountry = profile.country.isEmpty ? profileCountry : profile.country
        existingDatingImageUrls = profile.imageUrls
        profileGender = DatingGender(rawValue: profile.gender.uppercased()) ?? .other
        profileLookingFor = DatingLookingFor(rawValue: profile.lookingFor.uppercased()) ?? .everyone
        blindGender = profileGender
    }

    private func applyBlindFilters() {
        let query = blindSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let genderRaw = blindGenderFilter.targetRawValue

        filteredBlindProfiles = blindProfiles.filter { profile in
            let matchesQuery = query.isEmpty || profile.name.lowercased().contains(query)
            let matchesGender = genderRaw == nil || profile.gender.uppercased() == genderRaw
            return matchesQuery && matchesGender
        }
    }
}
