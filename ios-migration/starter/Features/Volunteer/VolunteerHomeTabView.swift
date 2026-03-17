import SwiftUI

struct VolunteerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            EventsListView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Events",
                        assetName: BrandAsset.tabEvents,
                        fallbackSystemName: "calendar"
                    )
                }

            JobsListView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Jobs",
                        assetName: BrandAsset.tabJobs,
                        fallbackSystemName: "briefcase"
                    )
                }

            MyActivityView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Activity",
                        assetName: BrandAsset.tabActivity,
                        fallbackSystemName: "clock"
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
