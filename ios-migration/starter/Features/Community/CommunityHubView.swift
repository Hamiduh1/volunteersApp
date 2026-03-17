import SwiftUI

struct CommunityHubView: View {
    let user: AppSessionUser

    var body: some View {
        NavigationStack {
            List {
                Section("Social") {
                    NavigationLink("MindLoom") {
                        MindLoomFeedView(user: user)
                    }
                    NavigationLink("Gallery Uploads") {
                        GalleryUploadsView(user: user)
                    }
                }

                Section("Commerce") {
                    NavigationLink("Marketplace") {
                        MarketplaceView(user: user)
                    }
                    NavigationLink("Sponsored & Garage") {
                        SponsoredContentView(user: user)
                    }
                }
            }
            .navigationTitle("Community")
        }
    }
}
