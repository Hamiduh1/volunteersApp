import SwiftUI

enum EmployerHomeTab: Hashable {
    case home
    case jobs
    case applications
    case profile
}

struct EmployerHomeTabView: View {
    let user: AppSessionUser
    @State private var selectedTab: EmployerHomeTab = .home

    var body: some View {
        TabView(selection: $selectedTab) {
            EmployerDashboardView(user: user) { tab in
                selectedTab = tab
            }
            .tabItem {
                BrandTabLabel(
                    title: "Home",
                    assetName: BrandAsset.tabDashboard,
                    fallbackSystemName: "house.fill"
                )
            }
            .tag(EmployerHomeTab.home)

            EmployerPostedJobsView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Jobs",
                        assetName: BrandAsset.tabPostedJobs,
                        fallbackSystemName: "briefcase.fill"
                    )
                }
                .tag(EmployerHomeTab.jobs)

            EmployerApplicationsReviewView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Applications",
                        assetName: BrandAsset.tabApplications,
                        fallbackSystemName: "person.3.fill"
                    )
                }
                .tag(EmployerHomeTab.applications)

            EmployerProfileSetupView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Profile",
                        assetName: BrandAsset.tabProfile,
                        fallbackSystemName: "person.crop.circle"
                    )
                }
                .tag(EmployerHomeTab.profile)
        }
    }
}
