import SwiftUI

enum OrganizerHomeTab: Hashable {
    case home
    case events
    case requests
    case summary
}

struct OrganizerHomeTabView: View {
    let user: AppSessionUser
    @State private var selectedTab: OrganizerHomeTab = .home

    var body: some View {
        TabView(selection: $selectedTab) {
            OrganizerDashboardView(user: user) { tab in
                selectedTab = tab
            }
                .tabItem {
                    BrandTabLabel(
                        title: "Home",
                        assetName: BrandAsset.tabDashboard,
                        fallbackSystemName: "house.fill"
                    )
                }
                .tag(OrganizerHomeTab.home)

            OrganizerHostedEventsView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Events",
                        assetName: BrandAsset.tabHosted,
                        fallbackSystemName: "calendar.badge.clock"
                    )
                }
                .tag(OrganizerHomeTab.events)

            OrganizerApplicationsReviewView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Requests",
                        assetName: BrandAsset.tabApplications,
                        fallbackSystemName: "person.2"
                    )
                }
                .tag(OrganizerHomeTab.requests)

            OrganizerSummaryView(user: user)
                .tabItem {
                    BrandTabLabel(
                        title: "Summary",
                        assetName: BrandAsset.tabControls,
                        fallbackSystemName: "chart.bar"
                    )
                }
                .tag(OrganizerHomeTab.summary)
        }
    }
}
