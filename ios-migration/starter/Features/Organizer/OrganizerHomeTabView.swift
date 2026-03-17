import SwiftUI

struct OrganizerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            OrganizerDashboardView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Home",
                        assetName: BrandAsset.tabDashboard,
                        fallbackSystemName: "house.fill"
                    )
                }

            OrganizerHostedEventsView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Events",
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

            OrganizerSummaryView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Summary",
                        assetName: BrandAsset.tabControls,
                        fallbackSystemName: "chart.bar"
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
        }
    }
}
