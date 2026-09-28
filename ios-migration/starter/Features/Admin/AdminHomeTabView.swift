import SwiftUI
import FirebaseFirestore

struct AdminHomeTabView: View {
    let user: AppSessionUser

    private var isOwnerOrAdmin: Bool {
        user.role == .owner || user.role == .admin
    }

    var body: some View {
        TabView {
            if isOwnerOrAdmin {
                OwnerDashboardView(user: user)
                    .tabItem {
                        BrandTabLabel(
                            title: "Dashboard",
                            assetName: BrandAsset.tabDashboard,
                            fallbackSystemName: "chart.line.uptrend.xyaxis"
                        )
                    }

                AdminControlsHubView(user: user)
                    .tabItem {
                        BrandTabLabel(
                            title: "Controls",
                            assetName: BrandAsset.tabControls,
                            fallbackSystemName: "switch.2"
                        )
                    }
            }

            SupportConsoleView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Support",
                        assetName: BrandAsset.tabSupport,
                        fallbackSystemName: "person.2.badge.gearshape"
                    )
                }

            GlobalWalletHomeView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Wallet",
                        assetName: BrandAsset.tabWallet,
                        fallbackSystemName: "wallet.pass"
                    )
                }

            CommunityHubView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Community",
                        assetName: BrandAsset.tabCommunity,
                        fallbackSystemName: "person.3.sequence"
                    )
                }

            AdvancedToolsHomeView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Tools",
                        assetName: BrandAsset.tabTools,
                        fallbackSystemName: "sparkles"
                    )
                }

            ProfileHomeView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Profile",
                        assetName: BrandAsset.tabProfile,
                        fallbackSystemName: "person"
                    )
                }
        }
    }
}

struct AdminControlsHubView: View {
    let user: AppSessionUser

    var body: some View {
        NavigationStack {
            List {
                AdminRoleResponsibilitiesSection(
                    screenKey: AdminRoleResponsibilitiesScreenKey.ADMIN_CONTROLS_HUB,
                    fallbackGuide: AdminRoleResponsibilitiesGuide(
                        roleTitle: "Admin Controls Coordinator",
                        mission: "Route the right workstream to the right operations role.",
                        responsibilities: [
                            "Direct payout, reports, KYC, and config work to trained associates.",
                            "Confirm each task has clear ownership and due time.",
                            "Escalate blocked issues and cross-team dependencies quickly.",
                            "Ensure actions align with policy and audit requirements."
                        ],
                        escalationRule: "Escalate unresolved critical incidents to owner immediately."
                    )
                )

                Section("Moderation") {
                    NavigationLink("Payout Queue") {
                        AdminPayoutQueueView(user: user)
                    }
                    NavigationLink("User Reports") {
                        OwnerUserReportsView()
                    }
                    NavigationLink("KYC Review") {
                        OwnerKYCReviewView()
                    }
                }

                Section("Configuration") {
                    NavigationLink("Fee Settings") {
                        OwnerFeeSettingsView()
                    }
                    NavigationLink("System Config") {
                        OwnerSystemConfigView()
                    }
                }
            }
            .navigationTitle("Admin Controls")
        }
    }
}

struct AdminRoleResponsibilitiesGuide {
    let roleTitle: String
    let mission: String
    let responsibilities: [String]
    let escalationRule: String?
}

struct AdminRoleResponsibilitiesScreenKey {
    static let OWNER_DASHBOARD = "owner_dashboard"
    static let SUPPORT_CONSOLE_ADMIN = "support_console_admin"
    static let SUPPORT_CONSOLE_ASSOCIATE = "support_console_associate"
    static let PAYOUT_QUEUE = "payout_queue"
    static let DISPUTES = "disputes"
    static let USER_REPORTS = "user_reports"
    static let KYC_REVIEW = "kyc_review"
    static let FEE_SETTINGS = "fee_settings"
    static let SYSTEM_CONFIG = "system_config"
    static let ADMIN_CONTROLS_HUB = "admin_controls_hub"
}

final class AdminRoleResponsibilitiesConfigStore: ObservableObject {
    static let shared = AdminRoleResponsibilitiesConfigStore()

    @Published private(set) var overrides: [String: AdminRoleResponsibilitiesGuide] = [:]
    private var listener: ListenerRegistration?

    private init() {
        startListening()
    }

    deinit {
        listener?.remove()
        listener = nil
    }

    func guide(for screenKey: String, fallback: AdminRoleResponsibilitiesGuide) -> AdminRoleResponsibilitiesGuide {
        overrides[screenKey] ?? fallback
    }

    private func startListening() {
        guard listener == nil else { return }
        listener = Firestore.firestore()
            .collection(FirestoreCollection.appConfig.rawValue)
            .document("role_responsibilities")
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self else { return }
                let parsed = self.parse(snapshot?.data() ?? [:])
                DispatchQueue.main.async {
                    self.overrides = parsed
                }
            }
    }

    private func parse(_ root: [String: Any]) -> [String: AdminRoleResponsibilitiesGuide] {
        let rawScreens = (root["screens"] as? [String: Any]) ?? root
        var parsed: [String: AdminRoleResponsibilitiesGuide] = [:]

        for (key, value) in rawScreens {
            guard let map = value as? [String: Any] else { continue }
            guard let roleTitle = (map["roleTitle"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines),
                  !roleTitle.isEmpty,
                  let mission = (map["mission"] as? String)?
                    .trimmingCharacters(in: .whitespacesAndNewlines),
                  !mission.isEmpty else {
                continue
            }

            let responsibilities = (map["responsibilities"] as? [Any])?
                .compactMap { item -> String? in
                    guard let text = item as? String else { return nil }
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    return trimmed.isEmpty ? nil : trimmed
                } ?? []
            guard !responsibilities.isEmpty else { continue }

            let escalation = (map["escalationRule"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .nonEmpty

            parsed[key] = AdminRoleResponsibilitiesGuide(
                roleTitle: roleTitle,
                mission: mission,
                responsibilities: responsibilities,
                escalationRule: escalation
            )
        }

        return parsed
    }
}

struct AdminRoleResponsibilitiesSection: View {
    let screenKey: String
    let fallbackGuide: AdminRoleResponsibilitiesGuide
    @StateObject private var configStore = AdminRoleResponsibilitiesConfigStore.shared

    var body: some View {
        Section("Role Responsibilities") {
            AdminRoleResponsibilitiesContent(
                guide: configStore.guide(for: screenKey, fallback: fallbackGuide)
            )
        }
    }
}

struct AdminRoleResponsibilitiesCard: View {
    let screenKey: String
    let fallbackGuide: AdminRoleResponsibilitiesGuide
    @StateObject private var configStore = AdminRoleResponsibilitiesConfigStore.shared

    var body: some View {
        GroupBox("Role Responsibilities") {
            AdminRoleResponsibilitiesContent(
                guide: configStore.guide(for: screenKey, fallback: fallbackGuide)
            )
            .padding(.top, 4)
        }
    }
}

private struct AdminRoleResponsibilitiesContent: View {
    let guide: AdminRoleResponsibilitiesGuide

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(guide.roleTitle)
                .font(.subheadline.weight(.semibold))
            Text(guide.mission)
                .font(.caption)
                .foregroundStyle(.secondary)
            ForEach(Array(guide.responsibilities.enumerated()), id: \.offset) { _, item in
                Text("- \(item)")
                    .font(.caption)
            }
            if let escalation = guide.escalationRule, !escalation.isEmpty {
                Text("Escalation: \(escalation)")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}
