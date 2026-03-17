import SwiftUI

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
