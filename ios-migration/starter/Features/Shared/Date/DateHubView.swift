import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

struct DateHubView: View {
    let user: AppSessionUser
    @StateObject private var viewModel: DateHubViewModel

    @State private var datingPhotoItems: [PhotosPickerItem] = []
    @State private var blindMediaItems: [PhotosPickerItem] = []
    @State private var datingPhotoData: [Data] = []
    @State private var blindMediaDrafts: [DateMediaUpload] = []

    @State private var showAgeDialog = false
    @State private var showPreferenceDialog = false
    @State private var showBlindJoinConfirm = false
    @State private var showDeleteDatingProfileConfirm = false
    @State private var ageInput = ""

    init(user: AppSessionUser) {
        self.user = user
        _viewModel = StateObject(wrappedValue: DateHubViewModel(user: user))
    }

    var body: some View {
        List {
            Section {
                Picker("Section", selection: $viewModel.selectedTab) {
                    ForEach(DateHubTab.allCases) { tab in
                        Text(tab.rawValue).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
            }

            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            switch viewModel.selectedTab {
            case .datingLoop:
                datingLoopSection
            case .blindDate:
                blindDateSection
            }
        }
        .navigationTitle("Dating Loop")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
        .onChange(of: datingPhotoItems) { _, items in
            Task { datingPhotoData = await loadPhotoData(from: items) }
        }
        .onChange(of: blindMediaItems) { _, items in
            Task { blindMediaDrafts = await loadMediaUploads(from: items) }
        }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
        .alert("Age Verification", isPresented: $showAgeDialog) {
            TextField("Enter age", text: $ageInput)
                .keyboardType(.numberPad)
            Button("Continue") {
                let age = Int(ageInput.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
                viewModel.verifyAge(age)
                ageInput = ""
                if age >= 18 {
                    showPreferenceDialog = !viewModel.hasDatingPreferences
                } else {
                    viewModel.statusMessage = "You must be 18+ to access this section."
                }
            }
            Button("Cancel", role: .cancel) {
                ageInput = ""
            }
        } message: {
            Text("To access Dating Loop/Blind Date, confirm you are 18 or older.")
        }
        .alert("Set Preferences", isPresented: $showPreferenceDialog) {
            Button("Use Current") {
                viewModel.saveDatingPreferences(gender: viewModel.profileGender, lookingFor: viewModel.profileLookingFor)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Save a dating profile with gender and preferences first.")
        }
        .alert("Confirm Blind Date Join", isPresented: $showBlindJoinConfirm) {
            Button(viewModel.isStaffExempt ? "Confirm & Join" : "Confirm & Pay") {
                Task {
                    await viewModel.joinBlindDate(newMediaData: blindMediaDrafts)
                    blindMediaDrafts = []
                    blindMediaItems = []
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(
                viewModel.isStaffExempt
                ? "Staff exemption detected. No fee will be charged."
                : "A wallet fee will be charged to join Blind Date."
            )
        }
        .alert("Delete Dating Profile", isPresented: $showDeleteDatingProfileConfirm) {
            Button("Delete", role: .destructive) {
                Task { await viewModel.deleteDatingProfile() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes your Dating Loop profile.")
        }
    }

    @ViewBuilder
    private var datingLoopSection: some View {
        Section("Dating Profile") {
            TextField("Name", text: $viewModel.profileName)
            TextField("Bio", text: $viewModel.profileBio, axis: .vertical)
                .lineLimit(2...5)
            TextField("Phone", text: $viewModel.profilePhone)
                .keyboardType(.phonePad)
            TextField("Country", text: $viewModel.profileCountry)

            Picker("Gender", selection: $viewModel.profileGender) {
                ForEach(DatingGender.allCases) { gender in
                    Text(gender.title).tag(gender)
                }
            }

            Picker("Looking For", selection: $viewModel.profileLookingFor) {
                ForEach(DatingLookingFor.allCases) { lookingFor in
                    Text(lookingFor.title).tag(lookingFor)
                }
            }

            PhotosPicker(
                selection: $datingPhotoItems,
                maxSelectionCount: 6,
                matching: .images
            ) {
                Label("Select profile photos", systemImage: "photo.on.rectangle")
            }
            if !datingPhotoData.isEmpty {
                Text("Selected new photos: \(datingPhotoData.count)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if !viewModel.existingDatingImageUrls.isEmpty {
                Text("Existing photos: \(viewModel.existingDatingImageUrls.count)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Button(viewModel.isLoading ? "Saving..." : (viewModel.hasDatingProfile ? "Update Dating Profile" : "Create Dating Profile")) {
                switch viewModel.gateBlindDateFlow() {
                case .proceed, .requirePreferences:
                    Task {
                        await viewModel.saveDatingProfile(newImageData: datingPhotoData)
                        datingPhotoData = []
                        datingPhotoItems = []
                    }
                case .requireAge:
                    showAgeDialog = true
                }
            }
            .disabled(viewModel.isLoading)

            if viewModel.hasDatingProfile {
                Button("Delete Dating Profile", role: .destructive) {
                    showDeleteDatingProfileConfirm = true
                }
                .disabled(viewModel.isLoading)
            }
        }

        Section("Browse Profiles") {
            if viewModel.datingProfiles.isEmpty && viewModel.isLoading {
                ProgressView("Loading profiles...")
            } else if viewModel.datingProfiles.isEmpty {
                Text("No dating profiles found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.datingProfiles) { profile in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(profile.name.isEmpty ? "Unnamed profile" : profile.name)
                            .font(.headline)
                        Text(profile.bio.isEmpty ? "No bio yet." : profile.bio)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("Gender: \(profile.gender) - Looking for: \(profile.lookingFor)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if profile.uid != user.uid {
                            Button("Send Chat Invitation") {
                                Task { await viewModel.sendDatingChatInvitation(to: profile.uid) }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    @ViewBuilder
    private var blindDateSection: some View {
        Section("Blind Date Status") {
            Text("Status: \(blindStatusTitle(viewModel.blindStatus))")
            if viewModel.isStaffExempt {
                Text("Staff fee exemption is active.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let chatId = viewModel.matchedChatId, !chatId.isEmpty {
                NavigationLink("Open Matched Chat") {
                    ConversationDetailView(user: user, conversationId: chatId)
                }
            }
        }

        if viewModel.canJoinBlindDate {
            Section("Join Blind Date") {
                TextField("Blind Date Bio", text: $viewModel.blindBio, axis: .vertical)
                    .lineLimit(2...5)
                Picker("Gender", selection: $viewModel.blindGender) {
                    ForEach(DatingGender.allCases) { gender in
                        Text(gender.title).tag(gender)
                    }
                }
                PhotosPicker(
                    selection: $blindMediaItems,
                    maxSelectionCount: 4,
                    matching: .any(of: [.images, .videos])
                ) {
                    Label("Select blind date media", systemImage: "photo.stack")
                }
                if !blindMediaDrafts.isEmpty {
                    let imageCount = blindMediaDrafts.filter { $0.contentType.hasPrefix("image/") }.count
                    let videoCount = blindMediaDrafts.filter { $0.contentType.hasPrefix("video/") }.count
                    Text("Selected: \(imageCount) images, \(videoCount) videos")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Button(viewModel.isLoading ? "Joining..." : "Join Blind Date") {
                    switch viewModel.gateBlindDateFlow() {
                    case .proceed:
                        showBlindJoinConfirm = true
                    case .requireAge:
                        showAgeDialog = true
                    case .requirePreferences:
                        showPreferenceDialog = true
                    }
                }
                .disabled(viewModel.isLoading || viewModel.blindBio.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || blindMediaDrafts.isEmpty)
            }
        } else {
            Section("Blind Date Actions") {
                Button(viewModel.isLoading ? "Rejoining..." : "Rejoin Loop") {
                    Task { await viewModel.rejoinBlindDate() }
                }
                .disabled(viewModel.isLoading)
            }
        }

        Section("Find People") {
            TextField("Search by name", text: Binding(
                get: { viewModel.blindSearchQuery },
                set: { viewModel.updateBlindSearchQuery($0) }
            ))

            Picker("Gender Filter", selection: Binding(
                get: { viewModel.blindGenderFilter },
                set: { viewModel.updateBlindGenderFilter($0) }
            )) {
                ForEach(BlindGenderFilter.allCases) { filter in
                    Text(filter.rawValue).tag(filter)
                }
            }

            if viewModel.filteredBlindProfiles.isEmpty {
                Text("No active profiles match your filter.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.filteredBlindProfiles) { profile in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(profile.name.isEmpty ? "Unknown user" : profile.name)
                            .font(.headline)
                        Text(profile.bio.isEmpty ? "No bio provided." : profile.bio)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("Gender: \(profile.gender)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Button("Send Blind Date Invitation") {
                            Task { await viewModel.sendBlindInvitation(to: profile.userId) }
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding(.vertical, 4)
                }
            }
        }

        if !viewModel.receivedInvitations.isEmpty {
            Section("Pending Invitations") {
                ForEach(viewModel.receivedInvitations) { invite in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(invite.senderName.isEmpty ? "Unknown sender" : invite.senderName)
                            .font(.headline)
                        Text("Status: \(invite.status.capitalized)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack {
                            Button("Accept") {
                                Task { await viewModel.acceptBlindInvitation(invite) }
                            }
                            .buttonStyle(.borderedProminent)

                            Button("Decline", role: .destructive) {
                                Task { await viewModel.declineBlindInvitation(invite) }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }

        if !viewModel.sentInvitations.isEmpty {
            Section("Sent Invitations") {
                ForEach(viewModel.sentInvitations) { invite in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(invite.recipientName.isEmpty ? "Unknown recipient" : invite.recipientName)
                            .font(.headline)
                        Text("Status: \(invite.status.capitalized)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }
        }

        if !viewModel.invitationTimeline.isEmpty {
            Section("Invitation Timeline") {
                ForEach(viewModel.invitationTimeline) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.otherUserName)
                            .font(.headline)
                        Text("\(item.direction.rawValue.capitalized) - \(item.status.capitalized)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if let updatedAt = item.updatedAt {
                            Text(updatedAt, style: .relative)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    private func blindStatusTitle(_ status: BlindDateUserStatusRecord) -> String {
        switch status {
        case .notJoined:
            return "Not Joined"
        case .active:
            return "Active"
        case .matched:
            return "Matched"
        case .expired:
            return "Expired"
        }
    }

    private func loadPhotoData(from items: [PhotosPickerItem]) async -> [Data] {
        var loaded: [Data] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self) {
                loaded.append(data)
            }
        }
        return loaded
    }

    private func loadMediaUploads(from items: [PhotosPickerItem]) async -> [DateMediaUpload] {
        var uploads: [DateMediaUpload] = []
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
            let supportedType = item.supportedContentTypes.first
            if let supportedType, supportedType.conforms(to: .movie) || supportedType.conforms(to: .video) {
                uploads.append(DateMediaUpload(data: data, fileExtension: "mp4", contentType: "video/mp4"))
            } else {
                if let image = UIImage(data: data), let jpeg = image.jpegData(compressionQuality: 0.88) {
                    uploads.append(DateMediaUpload(data: jpeg, fileExtension: "jpg", contentType: "image/jpeg"))
                } else {
                    uploads.append(DateMediaUpload(data: data, fileExtension: "jpg", contentType: "image/jpeg"))
                }
            }
        }
        return uploads
    }
}
