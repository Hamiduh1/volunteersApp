import SwiftUI

struct EmployerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            EmployerPostedJobsView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Posted Jobs",
                        assetName: BrandAsset.tabPostedJobs,
                        fallbackSystemName: "briefcase"
                    )
                }

            EmployerApplicationsReviewView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Applications",
                        assetName: BrandAsset.tabApplications,
                        fallbackSystemName: "person.3"
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
