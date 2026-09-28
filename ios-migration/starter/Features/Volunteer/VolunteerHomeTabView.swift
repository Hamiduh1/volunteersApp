import SwiftUI
import FirebaseFirestore

private enum VolunteerBottomTab: String, CaseIterable, Identifiable {
    case home
    case events
    case jobs
    case live
    case community

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Home"
        case .events: return "Events"
        case .jobs: return "Jobs"
        case .live: return "Live"
        case .community: return "Loop"
        }
    }

    var iconName: String {
        switch self {
        case .home: return "house.fill"
        case .events: return "calendar.badge.clock"
        case .jobs: return "briefcase.fill"
        case .live: return "video.fill"
        case .community: return "person.3.fill"
        }
    }

    var navigationTitle: String {
        switch self {
        case .home: return "Volunteers App"
        case .events: return "Events"
        case .jobs: return "Jobs"
        case .live: return "Live Streams"
        case .community: return "Community Hub"
        }
    }
}

private enum VolunteerDrawerRoute: Hashable {
    case profile
    case activity
    case wallet
    case marketplace
    case dateHub
    case mindLoom
    case sponsored
    case paymentMethods
    case chats
    case userDirectory
    case aiAssistant
    case accountSettings
    case securityPrivacy
    case supportCenter
    case privacyPolicy
    case termsAndConditions
    case notifications
    case communityAlerts
    case amlCft
    case howToUse
    case feedback
    case reportIssue
}

struct VolunteerHomeTabView: View {
    let user: AppSessionUser

    @State private var selectedTab: VolunteerBottomTab = .home
    @State private var showDrawer = false
    @State private var path: [VolunteerDrawerRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            ZStack(alignment: .bottom) {
                activeRootContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(.systemGroupedBackground))
            }
            .navigationTitle(path.isEmpty ? selectedTab.navigationTitle : "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if path.isEmpty {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            showDrawer = true
                        } label: {
                            Image(systemName: "line.3.horizontal")
                        }
                        .accessibilityLabel("Open navigation drawer")
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        path.append(.aiAssistant)
                    } label: {
                        Image(systemName: "sparkles")
                    }
                    .accessibilityLabel("AI Assistant")
                }
            }
            .safeAreaInset(edge: .bottom) {
                if path.isEmpty {
                    VolunteerBottomNavigationBar(
                        selectedTab: $selectedTab,
                        onSelect: { tab in
                            selectedTab = tab
                        }
                    )
                    .padding(.bottom, 4)
                    .background(.ultraThinMaterial)
                }
            }
            .navigationDestination(for: VolunteerDrawerRoute.self) { route in
                destinationView(for: route)
            }
            .sheet(isPresented: $showDrawer) {
                VolunteerDrawerSheet(
                    user: user,
                    onDismiss: { showDrawer = false },
                    onNavigateTab: { tab in
                        selectedTab = tab
                        path.removeAll()
                        showDrawer = false
                    },
                    onNavigateRoute: { route in
                        path.append(route)
                        showDrawer = false
                    }
                )
            }
        }
    }

    @ViewBuilder
    private var activeRootContent: some View {
        switch selectedTab {
        case .home:
            VolunteerMainHomeView(
                user: user,
                onOpenTab: { tab in selectedTab = tab },
                onOpenRoute: { route in path.append(route) }
            )
        case .events:
            EventsListView(user: user)
        case .jobs:
            JobsListView(user: user)
        case .live:
            LiveSessionsView(user: user)
        case .community:
            CommunityHubView(user: user)
        }
    }

    @ViewBuilder
    private func destinationView(for route: VolunteerDrawerRoute) -> some View {
        switch route {
        case .profile:
            ProfileHomeView(user: user)
        case .activity:
            MyActivityView(user: user)
        case .wallet:
            GlobalWalletHomeView(user: user)
        case .marketplace:
            MarketplaceView(user: user)
        case .dateHub:
            DateHubView(user: user)
        case .mindLoom:
            MindLoomFeedView(user: user)
        case .sponsored:
            SponsoredContentView(user: user)
        case .paymentMethods:
            PaymentMethodsView(user: user)
        case .chats:
            ConversationsListView(user: user)
        case .userDirectory:
            UserDirectoryView(user: user)
        case .aiAssistant:
            AIAssistantView()
        case .accountSettings:
            ProfileHomeView(user: user)
        case .securityPrivacy:
            AccountSecurityView(email: user.email ?? "")
        case .supportCenter:
            SupportCenterView()
        case .privacyPolicy:
            PrivacyPolicyView()
        case .termsAndConditions:
            TermsAndConditionsView()
        case .notifications:
            NotificationSettingsView(user: user)
        case .communityAlerts:
            CommunityAlertsView(user: user)
        case .amlCft:
            VolunteerGuidanceView(
                title: "AML/CFT Guide",
                subtitle: "Compliance and safe usage guidance for wallet and community features.",
                sections: [
                    "Verify identity and report suspicious activity immediately.",
                    "Never share OTP, verification codes, or recovery links.",
                    "Use trusted payment methods only and confirm recipient details."
                ]
            )
        case .howToUse:
            VolunteerGuidanceView(
                title: "How to Use",
                subtitle: "Quick guide to navigate volunteer loops in the app.",
                sections: [
                    "Use Home cards for Events, Jobs, Inbox, and Marketplace.",
                    "Open Community Loop to access MindLoom, Sponsored, and social tools.",
                    "Track applications and updates in My Activity and Social Inbox."
                ]
            )
        case .feedback:
            VolunteerFeedbackView(user: user)
        case .reportIssue:
            VolunteerReportIssueView(user: user)
        }
    }
}

private struct VolunteerBottomNavigationBar: View {
    @Binding var selectedTab: VolunteerBottomTab
    let onSelect: (VolunteerBottomTab) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(VolunteerBottomTab.allCases) { tab in
                let selected = selectedTab == tab
                Button {
                    onSelect(tab)
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: tab.iconName)
                            .font(.system(size: 16, weight: .semibold))
                        Text(tab.title)
                            .font(.caption2.weight(.semibold))
                    }
                    .foregroundStyle(selected ? .blue : .secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(selected ? Color.blue.opacity(0.12) : Color.clear)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }
}

private struct VolunteerMainHomeView: View {
    let user: AppSessionUser
    let onOpenTab: (VolunteerBottomTab) -> Void
    let onOpenRoute: (VolunteerDrawerRoute) -> Void

    private var greeting: String {
        let email = user.email?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let prefix = email.split(separator: "@").first.map(String.init) ?? ""
        return prefix.isEmpty ? "Welcome back" : "Welcome back, \(prefix)"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                tapHint
                quickActionRow
                communityGrid
                walletPromo
            }
            .padding(16)
        }
    }

    private var heroCard: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                colors: [Color(red: 0.06, green: 0.13, blue: 0.18), Color(red: 0.13, green: 0.23, blue: 0.30)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))

            VStack(alignment: .leading, spacing: 10) {
                Text(greeting)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                Text("The loop is alive. Pick a path and jump in.")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.86))

                HStack(spacing: 10) {
                    Button("Find Events") { onOpenTab(.events) }
                        .buttonStyle(.borderedProminent)
                        .tint(.white)
                        .foregroundStyle(.black)

                    Button("Open Wallet") { onOpenRoute(.wallet) }
                        .buttonStyle(.bordered)
                        .tint(.white)
                        .foregroundStyle(.white)
                }
                .padding(.top, 4)
            }
            .padding(20)
        }
        .frame(maxWidth: .infinity, minHeight: 170)
    }

    private var tapHint: some View {
        HStack(spacing: 10) {
            Image(systemName: "hand.tap.fill")
                .foregroundStyle(.blue)
            Text("Tap a card to start your next volunteer loop.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.blue.opacity(0.08))
        )
    }

    private var quickActionRow: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Quick actions")
                .font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    homeCard(title: "Events", subtitle: "Find upcoming opportunities", icon: "calendar", tint: .blue) {
                        onOpenTab(.events)
                    }
                    homeCard(title: "Jobs", subtitle: "Browse volunteer roles", icon: "briefcase", tint: .green) {
                        onOpenTab(.jobs)
                    }
                    homeCard(title: "Inbox", subtitle: "Messages and calls", icon: "bubble.left.and.bubble.right", tint: .purple) {
                        onOpenRoute(.chats)
                    }
                    homeCard(title: "Marketplace", subtitle: "Buy and sell items", icon: "storefront", tint: .orange) {
                        onOpenRoute(.marketplace)
                    }
                }
            }
        }
    }

    private var communityGrid: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Your community")
                .font(.headline)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                homeCard(title: "Community Hub", subtitle: "Social features and games", icon: "person.3", tint: .indigo, fixedWidth: nil) {
                    onOpenTab(.community)
                }
                homeCard(title: "Dating Loop", subtitle: "Find a connection", icon: "heart.fill", tint: .red, fixedWidth: nil) {
                    onOpenRoute(.dateHub)
                }
                homeCard(title: "MindLoom", subtitle: "Share and discover content", icon: "sparkles.tv", tint: .yellow, fixedWidth: nil) {
                    onOpenRoute(.mindLoom)
                }
                homeCard(title: "Sponsored", subtitle: "Local promos", icon: "megaphone.fill", tint: .brown, fixedWidth: nil) {
                    onOpenRoute(.sponsored)
                }
            }
        }
    }

    private var walletPromo: some View {
        Button {
            onOpenRoute(.wallet)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "wallet.pass.fill")
                    .font(.title3)
                    .foregroundStyle(.blue)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Global Wallet")
                        .font(.headline)
                    Text("Check balance, send funds, and manage history")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.blue.opacity(0.1))
            )
        }
        .buttonStyle(.plain)
    }

    private func homeCard(
        title: String,
        subtitle: String,
        icon: String,
        tint: Color,
        fixedWidth: CGFloat? = 190,
        onTap: @escaping () -> Void
    ) -> some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: icon)
                    .font(.headline)
                    .foregroundStyle(tint)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(tint.opacity(0.14)))
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.leading)
            }
            .padding(12)
            .frame(maxWidth: fixedWidth == nil ? .infinity : fixedWidth, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
        }
        .buttonStyle(.plain)
    }
}

private struct VolunteerDrawerSheet: View {
    let user: AppSessionUser
    let onDismiss: () -> Void
    let onNavigateTab: (VolunteerBottomTab) -> Void
    let onNavigateRoute: (VolunteerDrawerRoute) -> Void

    private var profileName: String {
        let clean = user.email?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return clean.split(separator: "@").first.map(String.init) ?? "Volunteer"
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 12) {
                        BrandSymbolView(
                            assetName: BrandAsset.avatarPlaceholder,
                            fallbackSystemName: "person.crop.circle.fill",
                            size: 44
                        )
                        VStack(alignment: .leading, spacing: 2) {
                            Text(profileName)
                                .font(.headline)
                            Text(user.email ?? "")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    .padding(.vertical, 2)

                    HStack(spacing: 10) {
                        quickDrawerChip("Community", icon: "person.3.fill") { onNavigateTab(.community) }
                        quickDrawerChip("Wallet", icon: "wallet.pass.fill") { onNavigateRoute(.wallet) }
                        quickDrawerChip("Inbox", icon: "bubble.left.and.bubble.right.fill") { onNavigateRoute(.chats) }
                    }
                }

                Section("Main") {
                    drawerRow("Home", "house.fill") { onNavigateTab(.home) }
                    drawerRow("My Profile", "person.crop.circle") { onNavigateRoute(.profile) }
                    drawerRow("My Activity", "checklist") { onNavigateRoute(.activity) }
                    drawerRow("Global Wallet", "wallet.pass.fill") { onNavigateRoute(.wallet) }
                    drawerRow("Payment Methods", "creditcard") { onNavigateRoute(.paymentMethods) }
                }

                Section("Personal") {
                    drawerRow("Account Settings", "person.text.rectangle") { onNavigateRoute(.accountSettings) }
                    drawerRow("Security & Privacy", "lock.shield") { onNavigateRoute(.securityPrivacy) }
                    drawerRow("Notification Settings", "bell.badge") { onNavigateRoute(.notifications) }
                    drawerRow("Community Alerts", "megaphone") { onNavigateRoute(.communityAlerts) }
                }

                Section("Support") {
                    drawerRow("Support Center", "person.2.badge.gearshape") { onNavigateRoute(.supportCenter) }
                    drawerRow("Privacy Policy", "hand.raised") { onNavigateRoute(.privacyPolicy) }
                    drawerRow("Terms & Conditions", "doc.text") { onNavigateRoute(.termsAndConditions) }
                    drawerRow("AML/CFT Guide", "shield.lefthalf.filled") { onNavigateRoute(.amlCft) }
                    drawerRow("How to Use", "questionmark.circle") { onNavigateRoute(.howToUse) }
                    drawerRow("AI Assistant", "sparkles") { onNavigateRoute(.aiAssistant) }
                    drawerRow("Feedback", "bubble.left.and.exclamationmark.bubble.right") { onNavigateRoute(.feedback) }
                    drawerRow("Report Issue", "exclamationmark.bubble") { onNavigateRoute(.reportIssue) }
                }

                Section {
                    Button(role: .destructive) {
                        try? AuthService.shared.signOut()
                        onDismiss()
                    } label: {
                        Label("Logout", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .navigationTitle("Menu")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") { onDismiss() }
                }
            }
        }
        .presentationDetents([.large])
    }

    private func quickDrawerChip(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                Text(title)
                    .lineLimit(1)
            }
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(
                Capsule()
                    .fill(Color(.secondarySystemGroupedBackground))
            )
        }
        .buttonStyle(.plain)
    }

    private func drawerRow(_ title: String, _ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Label(title, systemImage: icon)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct VolunteerGuidanceView: View {
    let title: String
    let subtitle: String
    let sections: [String]

    var body: some View {
        List {
            Section {
                Text(subtitle)
                    .foregroundStyle(.secondary)
            }
            Section("Guidance") {
                ForEach(Array(sections.enumerated()), id: \.offset) { index, item in
                    HStack(alignment: .top, spacing: 10) {
                        Text("\(index + 1).")
                            .font(.subheadline.weight(.bold))
                        Text(item)
                            .font(.subheadline)
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .navigationTitle(title)
    }
}

@MainActor
private final class VolunteerFeedbackViewModel: ObservableObject {
    @Published var message = ""
    @Published var rating = 4
    @Published var isSubmitting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let db = Firestore.firestore()

    func submit(user: AppSessionUser) async {
        let cleanMessage = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanMessage.isEmpty else {
            errorMessage = "Feedback message is required."
            return
        }
        isSubmitting = true
        errorMessage = nil
        statusMessage = nil
        defer { isSubmitting = false }

        do {
            try await db.collection("feedback").addDocument(data: [
                "uid": user.uid,
                "email": user.email ?? "",
                "rating": rating,
                "message": cleanMessage,
                "source": "ios_volunteer",
                "createdAt": FieldValue.serverTimestamp()
            ])
            message = ""
            statusMessage = "Thanks. Your feedback was submitted."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

private struct VolunteerFeedbackView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = VolunteerFeedbackViewModel()

    var body: some View {
        Form {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .foregroundStyle(.green)
                }
            }

            Section("Rate Your Experience") {
                Picker("Rating", selection: $viewModel.rating) {
                    ForEach(1...5, id: \.self) { value in
                        Text("\(value) Star\(value == 1 ? "" : "s")").tag(value)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section("Feedback") {
                TextEditor(text: $viewModel.message)
                    .frame(minHeight: 140)
            }

            Section {
                Button(viewModel.isSubmitting ? "Sending..." : "Submit Feedback") {
                    Task { await viewModel.submit(user: user) }
                }
                .disabled(viewModel.isSubmitting)
            }
        }
        .navigationTitle("Feedback")
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

@MainActor
private final class VolunteerReportIssueViewModel: ObservableObject {
    @Published var title = ""
    @Published var details = ""
    @Published var category = "General"
    @Published var isSubmitting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    let categories = ["General", "Payments", "Events", "Jobs", "Chat", "Community", "Security"]
    private let db = Firestore.firestore()

    func submit(user: AppSessionUser) async {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDetails = details.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else {
            errorMessage = "Issue title is required."
            return
        }
        guard !cleanDetails.isEmpty else {
            errorMessage = "Issue details are required."
            return
        }
        isSubmitting = true
        errorMessage = nil
        statusMessage = nil
        defer { isSubmitting = false }

        do {
            try await db.collection("user_reports").addDocument(data: [
                "reportedByUid": user.uid,
                "reporterEmail": user.email ?? "",
                "title": cleanTitle,
                "description": cleanDetails,
                "category": category,
                "status": "open",
                "source": "ios_volunteer",
                "createdAt": FieldValue.serverTimestamp()
            ])
            title = ""
            details = ""
            statusMessage = "Issue submitted successfully."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

private struct VolunteerReportIssueView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = VolunteerReportIssueViewModel()

    var body: some View {
        Form {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .foregroundStyle(.green)
                }
            }

            Section("Issue Details") {
                TextField("Issue title", text: $viewModel.title)
                Picker("Category", selection: $viewModel.category) {
                    ForEach(viewModel.categories, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
                TextEditor(text: $viewModel.details)
                    .frame(minHeight: 160)
            }

            Section {
                Button(viewModel.isSubmitting ? "Submitting..." : "Submit Report") {
                    Task { await viewModel.submit(user: user) }
                }
                .disabled(viewModel.isSubmitting)
            }
        }
        .navigationTitle("Report Issue")
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
