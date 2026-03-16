import SwiftUI
import PhotosUI

struct DateHubView: View {
    let user: AppSessionUser
    @StateObject private var viewModel: DateHubViewModel

    @State private var datingPhotoItems: [PhotosPickerItem] = []
    @State private var blindPhotoItems: [PhotosPickerItem] = []
    @State private var datingPhotoData: [Data] = []
    @State private var blindPhotoData: [Data] = []

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
        .navigationTitle("Dating & Blind Date")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
        .onChange(of: datingPhotoItems) { _, items in
            Task { datingPhotoData = await loadPhotoData(from: items) }
        }
        .onChange(of: blindPhotoItems) { _, items in
            Task { blindPhotoData = await loadPhotoData(from: items) }
        }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    @ViewBuilder
    private var datingLoopSection: some View {
        Section("Your Dating Profile") {
            TextField("Name", text: $viewModel.profileName)
            TextField("Bio", text: $viewModel.profileBio, axis: .vertical)
                .lineLimit(2...4)
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
                maxSelectionCount: 4,
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

            Button(viewModel.isLoading ? "Saving..." : "Save Dating Profile") {
                Task {
                    await viewModel.saveDatingProfile(newImageData: datingPhotoData)
                    datingPhotoData = []
                    datingPhotoItems = []
                }
            }
            .disabled(viewModel.isLoading)
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
                    .lineLimit(2...4)
                Picker("Gender", selection: $viewModel.blindGender) {
                    ForEach(DatingGender.allCases) { gender in
                        Text(gender.title).tag(gender)
                    }
                }
                PhotosPicker(
                    selection: $blindPhotoItems,
                    maxSelectionCount: 4,
                    matching: .images
                ) {
                    Label("Select blind date media", systemImage: "photo.stack")
                }
                if !blindPhotoData.isEmpty {
                    Text("Selected media: \(blindPhotoData.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Button(viewModel.isLoading ? "Joining..." : "Join Blind Date") {
                    Task {
                        await viewModel.joinBlindDate(newMediaData: blindPhotoData)
                        blindPhotoData = []
                        blindPhotoItems = []
                    }
                }
                .disabled(viewModel.isLoading)
            }
        } else {
            Section("Blind Date Actions") {
                Button(viewModel.isLoading ? "Rejoining..." : "Rejoin Loop") {
                    Task { await viewModel.rejoinBlindDate() }
                }
                .disabled(viewModel.isLoading)
            }
        }

        if !viewModel.filteredBlindProfiles.isEmpty {
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
}
