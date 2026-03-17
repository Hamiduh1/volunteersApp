import SwiftUI
import UIKit

enum BrandAsset {
    static let appLogo = "brand_app_logo"
    static let companyMark = "brand_company_mark"
    static let avatarPlaceholder = "brand_avatar_placeholder"

    static let tabEvents = "tab_events"
    static let tabJobs = "tab_jobs"
    static let tabActivity = "tab_activity"
    static let tabWallet = "tab_wallet"
    static let tabCommunity = "tab_community"
    static let tabTools = "tab_tools"
    static let tabProfile = "tab_profile"
    static let tabHosted = "tab_hosted"
    static let tabApplications = "tab_applications"
    static let tabPostedJobs = "tab_posted_jobs"
    static let tabDashboard = "tab_dashboard"
    static let tabControls = "tab_controls"
    static let tabSupport = "tab_support"

    static let hubMindLoom = "hub_mindloom"
    static let hubGallery = "hub_gallery"
    static let hubMarketplace = "hub_marketplace"
    static let hubSponsored = "hub_sponsored"
}

enum BrandAssetCatalog {
    static func hasImage(named name: String) -> Bool {
        UIImage(named: name) != nil
    }
}

struct BrandSymbolView: View {
    let assetName: String
    let fallbackSystemName: String
    var size: CGFloat = 20
    var useTemplate: Bool = true
    var tint: Color = .primary

    var body: some View {
        resolvedImage
            .renderingMode(useTemplate ? .template : .original)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(tint)
            .accessibilityHidden(true)
    }

    private var resolvedImage: Image {
        if BrandAssetCatalog.hasImage(named: assetName) {
            return Image(assetName)
        }
        return Image(systemName: fallbackSystemName)
    }
}

struct BrandTabLabel: View {
    let title: String
    let assetName: String
    let fallbackSystemName: String

    var body: some View {
        VStack(spacing: 2) {
            BrandSymbolView(
                assetName: assetName,
                fallbackSystemName: fallbackSystemName,
                size: 18
            )
            Text(title)
        }
    }
}
