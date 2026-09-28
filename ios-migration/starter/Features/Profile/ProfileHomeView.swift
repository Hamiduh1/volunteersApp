import SwiftUI
import PhotosUI
import UIKit

struct ProfileHomeView: View {
    private enum SectionAnchor {
        static let dashboard = "profile_dashboard"
        static let details = "profile_details"
        static let privacy = "profile_privacy"
        static let signout = "profile_signout"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = ProfileHomeViewModel()
    @State private var selectedPhotoItem: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                Form {
                    Section {
                        dashboardHomeSection(proxy: proxy)
                    }
                    .id(SectionAnchor.dashboard)

                    Section {
                        HStack(spacing: 14) {
                            avatarView
                                .frame(width: 72, height: 72)

                            VStack(alignment: .leading, spacing: 4) {
                                Text(displayName)
                                    .font(.headline)
                                Text(viewModel.profile.email.isEmpty ? (user.email ?? "") : viewModel.profile.email)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text("Role: \(roleText)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)

                        PhotosPicker(
                            selection: $selectedPhotoItem,
                            matching: .images,
                            preferredItemEncoding: .automatic
                        ) {
                            if viewModel.isUploadingImage {
                                HStack {
                                    ProgressView()
                                    Text("Uploading image...")
                                }
                            } else {
                                Label("Change Profile Photo", systemImage: "photo")
                            }
                        }
                    }

                    if let status = viewModel.statusMessage, !status.isEmpty {
                        Section {
                            Text(status)
                                .font(.subheadline)
                                .foregroundStyle(.green)
                        }
                    }

                    Section("Profile Details") {
                        TextField("Name", text: $viewModel.name)
                        TextField("Username", text: $viewModel.username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        TextField("Phone Number", text: $viewModel.phoneNumber)
                            .keyboardType(.phonePad)

                        Button {
                            Task { await viewModel.save(uid: user.uid) }
                        } label: {
                            if viewModel.isSaving {
                                ProgressView()
                            } else {
                                Text("Save Profile")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isSaving)
                    }
                    .id(SectionAnchor.details)

                    Section("Privacy & Support") {
                        NavigationLink("AI Assistant") {
                            AIAssistantView()
                        }
                        NavigationLink("Notification Settings") {
                            NotificationSettingsView(user: user)
                        }
                        NavigationLink("Community Alerts") {
                            CommunityAlertsView(user: user)
                        }
                        NavigationLink("Privacy Policy") {
                            PrivacyPolicyView()
                        }
                        NavigationLink("Terms & Conditions") {
                            TermsAndConditionsView()
                        }
                        NavigationLink("Support Center") {
                            SupportCenterView()
                        }
                        NavigationLink("Security Center") {
                            AccountSecurityView(email: viewModel.profile.email.isEmpty ? (user.email ?? "") : viewModel.profile.email)
                        }
                    }
                    .id(SectionAnchor.privacy)

                    Section {
                        Button("Sign Out", role: .destructive) {
                            try? AuthService.shared.signOut()
                        }
                    }
                    .id(SectionAnchor.signout)
                }
                .navigationTitle("Profile")
                .task { await viewModel.refresh(uid: user.uid) }
                .refreshable { await viewModel.refresh(uid: user.uid) }
                .onChange(of: selectedPhotoItem) { newValue in
                    guard let newValue else { return }
                    Task {
                        if let data = try? await newValue.loadTransferable(type: Data.self) {
                            let imageData: Data
                            if let uiImage = UIImage(data: data),
                               let jpeg = uiImage.jpegData(compressionQuality: 0.85) {
                                imageData = jpeg
                            } else {
                                imageData = data
                            }
                            await viewModel.uploadProfileImage(uid: user.uid, jpegData: imageData)
                        }
                    }
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
        }
    }

    @ViewBuilder
    private func dashboardHomeSection(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Dashboard Home")
                .font(.headline)

            Text("Android-style volunteer profile command center and quick navigation.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                dashboardMetric(value: roleText, label: "Role")
                dashboardMetric(
                    value: viewModel.profile.email.isEmpty ? (user.email ?? "--") : viewModel.profile.email,
                    label: "Email"
                )
                dashboardMetric(
                    value: viewModel.phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "--" : viewModel.phoneNumber,
                    label: "Phone"
                )
            }

            HStack(spacing: 10) {
                NavigationLink {
                    MyActivityView(user: user)
                } label: {
                    dashboardTileLabel(title: "My Activity", icon: "checklist")
                }
                .buttonStyle(.plain)

                NavigationLink {
                    GlobalWalletHomeView(user: user)
                } label: {
                    dashboardTileLabel(title: "Wallet", icon: "wallet.pass.fill")
                }
                .buttonStyle(.plain)

                NavigationLink {
                    CommunityHubView(user: user)
                } label: {
                    dashboardTileLabel(title: "Community", icon: "person.3.fill")
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 8) {
                dashboardQuickButton("Details") {
                    withAnimation { proxy.scrollTo(SectionAnchor.details, anchor: .top) }
                }
                dashboardQuickButton("Privacy") {
                    withAnimation { proxy.scrollTo(SectionAnchor.privacy, anchor: .top) }
                }
                dashboardQuickButton("Sign out") {
                    withAnimation { proxy.scrollTo(SectionAnchor.signout, anchor: .top) }
                }
            }

            HStack(spacing: 8) {
                NavigationLink {
                    EventsListView(user: user)
                } label: {
                    Label("Events", systemImage: "calendar.badge.clock")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)

                NavigationLink {
                    JobsListView(user: user)
                } label: {
                    Label("Jobs", systemImage: "briefcase.fill")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)

                NavigationLink {
                    AIAssistantView()
                } label: {
                    Label("AI Assistant", systemImage: "sparkles")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)
            }
        }
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.caption.weight(.bold))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.65)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardTileLabel(title: String, icon: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.blue)
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardQuickButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .buttonStyle(.bordered)
            .font(.caption.weight(.semibold))
    }

    @ViewBuilder
    private var avatarView: some View {
        if let urlString = viewModel.profileImageUrl ?? viewModel.profile.profileImageUrl,
           let url = URL(string: urlString),
           !urlString.isEmpty {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                case .failure:
                    ZStack {
                        Circle().fill(Color.gray.opacity(0.2))
                        BrandSymbolView(
                            assetName: BrandAsset.avatarPlaceholder,
                            fallbackSystemName: "person.fill",
                            size: 28,
                            tint: .secondary
                        )
                    }
                case .empty:
                    ZStack {
                        Circle().fill(Color.gray.opacity(0.25))
                        ProgressView()
                    }
                @unknown default:
                    ZStack {
                        Circle().fill(Color.gray.opacity(0.2))
                        BrandSymbolView(
                            assetName: BrandAsset.avatarPlaceholder,
                            fallbackSystemName: "person.fill",
                            size: 28,
                            tint: .secondary
                        )
                    }
                }
            }
            .clipShape(Circle())
        } else {
            ZStack {
                Circle().fill(Color.gray.opacity(0.2))
                BrandSymbolView(
                    assetName: BrandAsset.avatarPlaceholder,
                    fallbackSystemName: "person.fill",
                    size: 28,
                    tint: .secondary
                )
            }
        }
    }

    private var displayName: String {
        if !viewModel.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return viewModel.name
        }
        if !viewModel.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return viewModel.username
        }
        return user.email ?? user.uid
    }

    private var roleText: String {
        let mapped = viewModel.profile.role.isEmpty ? user.role.rawValue : viewModel.profile.role
        return mapped.replacingOccurrences(of: "_", with: " ").capitalized
    }
}
