import SwiftUI

struct OrganizerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            OrganizerHostedEventsView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Hosted",
                        assetName: BrandAsset.tabHosted,
                        fallbackSystemName: "calendar.badge.clock"
                    )
                }

            OrganizerApplicationsReviewView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Applications",
                        assetName: BrandAsset.tabApplications,
                        fallbackSystemName: "person.2"
                    )
                }

            OrganizerWalletView(user: user)
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
