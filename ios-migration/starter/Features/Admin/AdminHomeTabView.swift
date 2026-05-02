import SwiftUI

struct AdminHomeTabView: View {
    let user: AppSessionUser

    private var isOwner: Bool { user.role == .owner }
    private var isOwnerOrAdmin: Bool { user.role == .owner || user.role == .admin }

    var body: some View {
        TabView {
            if isOwnerOrAdmin {
                Group {
                    if isOwner {
                        OwnerDashboardView(user: user)
                    } else {
                        AdminDashboardView(user: user)
                    }
                }
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

            NavigationStack {
                GlobalWalletHomeView(user: user)
            }
                .tabItem {
                    BrandTabLabel(
                        title: "Wallet",
                        assetName: BrandAsset.tabWallet,
                        fallbackSystemName: "wallet.pass"
                    )
                }

            NavigationStack {
                CommunityHubView(user: user)
            }
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
    let embedded: Bool

    init(user: AppSessionUser, embedded: Bool = false) {
        self.user = user
        self.embedded = embedded
    }

    var body: some View {
        Group {
            if embedded {
                controlsContent
            } else {
                NavigationStack {
                    controlsContent
                }
            }
        }
    }

    @ViewBuilder
    private var controlsContent: some View {
            List {
                Section("Moderation") {
                    NavigationLink("Payout Queue") {
                        AdminPayoutQueueView(user: user)
                    }
                    NavigationLink("Deposit Queue") {
                        AdminDepositQueueView(user: user)
                    }
                    NavigationLink("User Reports") {
                        OwnerUserReportsView()
                    }
                    NavigationLink("KYC Review") {
                        OwnerKYCReviewView()
                    }
                }

                if user.role == .owner {
                    Section("Configuration") {
                        NavigationLink("Fee Settings") {
                            OwnerFeeSettingsView()
                        }
                        NavigationLink("System Config") {
                            OwnerSystemConfigView()
                        }
                    }
                } else {
                    Section("Configuration") {
                        Text("Configuration is owner-only.")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Admin Controls")
    }
}
