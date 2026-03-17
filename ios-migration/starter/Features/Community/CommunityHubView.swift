import SwiftUI

struct CommunityHubView: View {
    let user: AppSessionUser

    var body: some View {
        NavigationStack {
            List {
                Section("Social") {
                    NavigationLink {
                        MindLoomFeedView(user: user)
                    } label: {
                        hubRow(
                            title: "MindLoom",
                            assetName: BrandAsset.hubMindLoom,
                            fallbackSystemName: "sparkles.tv"
                        )
                    }

                    NavigationLink {
                        GalleryUploadsView(user: user)
                    } label: {
                        hubRow(
                            title: "Gallery Uploads",
                            assetName: BrandAsset.hubGallery,
                            fallbackSystemName: "photo.on.rectangle"
                        )
                    }
                }

                Section("Commerce") {
                    NavigationLink {
                        MarketplaceView(user: user)
                    } label: {
                        hubRow(
                            title: "Marketplace",
                            assetName: BrandAsset.hubMarketplace,
                            fallbackSystemName: "storefront"
                        )
                    }

                    NavigationLink {
                        SponsoredContentView(user: user)
                    } label: {
                        hubRow(
                            title: "Sponsored & Garage",
                            assetName: BrandAsset.hubSponsored,
                            fallbackSystemName: "megaphone"
                        )
                    }
                }
            }
            .navigationTitle("Community")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    BrandSymbolView(
                        assetName: BrandAsset.companyMark,
                        fallbackSystemName: "person.3.sequence",
                        size: 20,
                        useTemplate: false
                    )
                }
            }
        }
    }

    @ViewBuilder
    private func hubRow(title: String, assetName: String, fallbackSystemName: String) -> some View {
        HStack(spacing: 10) {
            BrandSymbolView(
                assetName: assetName,
                fallbackSystemName: fallbackSystemName,
                size: 18
            )
            Text(title)
        }
    }
}
