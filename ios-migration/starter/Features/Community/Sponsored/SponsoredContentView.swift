import SwiftUI

private enum SponsoredSection: String, CaseIterable, Identifiable {
    case ads = "Sponsored Ads"
    case garage = "Garage Sales"

    var id: String { rawValue }
}

struct SponsoredContentView: View {
    private enum SectionAnchor {
        static let dashboard = "sponsored_dashboard"
        static let sectionPicker = "sponsored_picker"
        static let ads = "sponsored_ads"
        static let garage = "sponsored_garage"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = SponsoredContentViewModel()
    @State private var selectedSection: SponsoredSection = .ads
    @State private var showCreateAd = false
    @State private var showCreateGarageSale = false
    @State private var editingAd: AdvertisementRecord?
    @State private var pendingDeleteAd: AdvertisementRecord?
    @State private var expandedAdIds: Set<String> = []
    @State private var expandedGarageIds: Set<String> = []
    @State private var paymentTargetSale: GarageSaleRecord?
    @State private var payAmountText = ""
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    dashboardHomeSection(proxy: proxy)
                }
                .id(SectionAnchor.dashboard)

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Section {
                        Text(status)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    Picker("Section", selection: $selectedSection) {
                        ForEach(SponsoredSection.allCases) { section in
                            Text(section.rawValue).tag(section)
                        }
                    }
                }
                .id(SectionAnchor.sectionPicker)
                .pickerStyle(.segmented)

                if selectedSection == .ads {
                    sponsoredAdsSection
                        .id(SectionAnchor.ads)
                } else {
                    garageSalesSection
                        .id(SectionAnchor.garage)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("Advertisements")
        .overlay(alignment: .bottomTrailing) {
            Button {
                if selectedSection == .ads {
                    showCreateAd = true
                } else {
                    showCreateGarageSale = true
                }
            } label: {
                Label(selectedSection == .ads ? "Post Ad" : "Post Garage Sale", systemImage: "plus")
                    .fontWeight(.semibold)
            }
            .buttonStyle(.borderedProminent)
            .padding(.trailing, 16)
            .padding(.bottom, 22)
        }
        .sheet(isPresented: $showCreateAd) {
            NavigationStack {
                CreateAdvertisementView(user: user, viewModel: viewModel) {
                    showCreateAd = false
                }
            }
        }
        .sheet(isPresented: $showCreateGarageSale) {
            NavigationStack {
                CreateGarageSaleView(user: user, viewModel: viewModel) {
                    showCreateGarageSale = false
                }
            }
        }
        .sheet(isPresented: Binding(
            get: { editingAd != nil },
            set: { if !$0 { editingAd = nil } }
        )) {
            if let ad = editingAd {
                NavigationStack {
                    CreateAdvertisementView(user: user, viewModel: viewModel, existingAd: ad) {
                        editingAd = nil
                    }
                }
            }
        }
        .confirmationDialog("Delete Advertisement", isPresented: Binding(
            get: { pendingDeleteAd != nil },
            set: { if !$0 { pendingDeleteAd = nil } }
        ), titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                guard let adId = pendingDeleteAd?.id, !adId.isEmpty else {
                    pendingDeleteAd = nil
                    return
                }
                Task {
                    await viewModel.deleteAdvertisement(adId: adId)
                    pendingDeleteAd = nil
                }
            }
            Button("Cancel", role: .cancel) { pendingDeleteAd = nil }
        }
        .sheet(isPresented: Binding(
            get: { paymentTargetSale != nil },
            set: { if !$0 { paymentTargetSale = nil; payAmountText = "" } }
        )) {
            if let sale = paymentTargetSale {
                NavigationStack {
                    GarageSalePaymentSheet(
                        saleTitle: sale.title ?? "Garage Sale",
                        amountText: $payAmountText,
                        onCancel: {
                            paymentTargetSale = nil
                            payAmountText = ""
                        },
                        onPayNow: {
                            let amount = Double(payAmountText.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
                            guard amount > 0, let saleId = sale.id, !saleId.isEmpty else { return }
                            Task {
                                await viewModel.submitGarageSalePayment(
                                    buyer: user,
                                    sellerId: sale.ownerId ?? "",
                                    garageSaleId: saleId,
                                    amount: amount
                                )
                                paymentTargetSale = nil
                                payAmountText = ""
                            }
                        }
                    )
                }
            }
        }
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    @ViewBuilder
    private func dashboardHomeSection(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Dashboard Home")
                .font(.headline)

            Text("Android-style sponsored control center for ads and garage sales.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                dashboardMetric(value: "\(viewModel.advertisements.count)", label: "Ads")
                dashboardMetric(value: "\(viewModel.garageSales.count)", label: "Garage")
                dashboardMetric(value: "\(viewModel.isLoading ? "..." : "Ready")", label: "Status")
            }

            HStack(spacing: 10) {
                dashboardTile(
                    title: "Post Ad",
                    icon: "megaphone.fill"
                ) {
                    selectedSection = .ads
                    showCreateAd = true
                }
                dashboardTile(
                    title: "Post Garage",
                    icon: "storefront.fill"
                ) {
                    selectedSection = .garage
                    showCreateGarageSale = true
                }
                dashboardTile(
                    title: "Refresh",
                    icon: "arrow.clockwise.circle.fill"
                ) {
                    Task { await viewModel.refresh() }
                }
            }

            HStack(spacing: 8) {
                dashboardQuickButton("Picker") {
                    withAnimation { proxy.scrollTo(SectionAnchor.sectionPicker, anchor: .top) }
                }
                dashboardQuickButton("Ads") {
                    selectedSection = .ads
                    withAnimation { proxy.scrollTo(SectionAnchor.ads, anchor: .top) }
                }
                dashboardQuickButton("Garage") {
                    selectedSection = .garage
                    withAnimation { proxy.scrollTo(SectionAnchor.garage, anchor: .top) }
                }
            }

            HStack(spacing: 8) {
                NavigationLink {
                    CommunityHubView(user: user)
                } label: {
                    Label("Community Dashboard", systemImage: "square.grid.2x2.fill")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)

                NavigationLink {
                    AIAssistantView()
                } label: {
                    Label("AI Assistant", systemImage: "sparkles")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)
            }
        }
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardTile(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.subheadline)
                    .foregroundStyle(.blue)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func dashboardQuickButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .buttonStyle(.bordered)
            .font(.caption.weight(.semibold))
    }

    @ViewBuilder
    private var sponsoredAdsSection: some View {
        Section("Sponsored Ads") {
            if viewModel.isLoading && viewModel.advertisements.isEmpty {
                ProgressView("Loading ads...")
            } else if viewModel.advertisements.isEmpty {
                Text("No ads available.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.advertisements) { ad in
                    adCard(ad)
                }
            }
        }
    }

    @ViewBuilder
    private func adCard(_ ad: AdvertisementRecord) -> some View {
        let adId = ad.id ?? "ad-\(ad.title ?? "untitled")-\(ad.timestamp?.seconds ?? 0)"
        let isOwner = (ad.ownerId ?? "") == user.uid
        let isExpanded = expandedAdIds.contains(adId)

        VStack(alignment: .leading, spacing: 10) {
            adMediaPager(ad)

            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(ad.title ?? "Ad")
                        .font(.headline)
                        .lineLimit(1)
                    Text("Sponsored by \(ad.sponsor ?? "Volunteer App Partner")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if isOwner {
                    Menu {
                        Button("Edit") { editingAd = ad }
                        Button("Delete", role: .destructive) { pendingDeleteAd = ad }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .font(.title3)
                    }
                }
                Button {
                    toggleExpanded(id: adId, ads: true)
                } label: {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.callout.weight(.semibold))
                }
                .buttonStyle(.plain)
            }

            if isExpanded {
                Text(ad.description ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                HStack(spacing: 8) {
                    if let target = ad.targetUrl, let url = URL(string: target) {
                        Button("Learn More") { openURL(url) }
                            .buttonStyle(.borderedProminent)
                    }
                    if !isOwner {
                        Button("Chat") {
                            Task { await viewModel.sendAdChatInvitation(user: user, ad: ad) }
                        }
                        .buttonStyle(.bordered)

                        if let phone = ad.ownerPhone, let tel = telURL(from: phone) {
                            Button("Call") { openURL(tel) }
                                .buttonStyle(.bordered)
                        }
                    }
                }
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(uiColor: .secondarySystemBackground))
        )
        .listRowInsets(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
        .listRowSeparator(.hidden)
    }

    @ViewBuilder
    private func adMediaPager(_ ad: AdvertisementRecord) -> some View {
        let media = normalizedAdMedia(ad)
        if media.isEmpty {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(uiColor: .tertiarySystemFill))
                Image(systemName: "photo")
                    .foregroundStyle(.secondary)
            }
            .frame(height: 180)
        } else {
            TabView {
                ForEach(Array(media.enumerated()), id: \.offset) { _, item in
                    mediaPreview(item)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
            .frame(height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    @ViewBuilder
    private var garageSalesSection: some View {
        Section("Garage Sales") {
            if viewModel.isLoading && viewModel.garageSales.isEmpty {
                ProgressView("Loading garage sales...")
            } else if viewModel.garageSales.isEmpty {
                Text("No garage sales available.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.garageSales) { sale in
                    garageSaleCard(sale)
                }
            }
        }
    }

    @ViewBuilder
    private func garageSaleCard(_ sale: GarageSaleRecord) -> some View {
        let saleId = sale.id ?? "sale-\(sale.title ?? "untitled")-\(sale.timestamp?.seconds ?? 0)"
        let isExpanded = expandedGarageIds.contains(saleId)

        VStack(alignment: .leading, spacing: 10) {
            garageMediaPager(sale)

            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(sale.title ?? "Garage Sale")
                        .font(.headline)
                        .lineLimit(1)
                    Text("Garage sale listing")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    toggleExpanded(id: saleId, ads: false)
                } label: {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.callout.weight(.semibold))
                }
                .buttonStyle(.plain)
            }

            if isExpanded {
                Text(sale.description ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Contact")
                        .font(.subheadline.weight(.semibold))
                    Text(sale.contactName ?? "Contact not listed")
                    if let phone = sale.contactPhone, !phone.isEmpty {
                        Text("Phone: \(phone)")
                    }
                    if let email = sale.contactEmail, !email.isEmpty {
                        Text("Email: \(email)")
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Address")
                        .font(.subheadline.weight(.semibold))
                    Text(addressLine(for: sale).isEmpty ? "Address not listed" : addressLine(for: sale))
                }

                HStack(spacing: 8) {
                    Button("Chat") {
                        Task { await viewModel.sendGarageSaleChatInvitation(user: user, sale: sale) }
                    }
                    .buttonStyle(.bordered)

                    if let phone = sale.contactPhone, let tel = telURL(from: phone) {
                        Button("Call") { openURL(tel) }
                            .buttonStyle(.bordered)
                    }
                    if let email = sale.contactEmail, let emailURL = emailURL(from: email) {
                        Button("Email") { openURL(emailURL) }
                            .buttonStyle(.bordered)
                    }
                    if let mapURL = mapURL(for: sale) {
                        Button("Map") { openURL(mapURL) }
                            .buttonStyle(.bordered)
                    }
                }

                Button("Pay Total Sales") {
                    paymentTargetSale = sale
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(uiColor: .secondarySystemBackground))
        )
        .listRowInsets(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
        .listRowSeparator(.hidden)
    }

    @ViewBuilder
    private func garageMediaPager(_ sale: GarageSaleRecord) -> some View {
        let media = sale.media ?? []
        if media.isEmpty {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(uiColor: .tertiarySystemFill))
                Image(systemName: "photo")
                    .foregroundStyle(.secondary)
            }
            .frame(height: 180)
        } else {
            TabView {
                ForEach(Array(media.enumerated()), id: \.offset) { _, item in
                    mediaPreview(item)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
            .frame(height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    @ViewBuilder
    private func mediaPreview(_ media: GarageSaleMediaRecord) -> some View {
        let type = (media.type ?? "image").lowercased()
        if type == "image", let raw = media.url, let url = URL(string: raw) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .empty:
                    ProgressView()
                default:
                    fallbackMediaPreview(type: type, name: media.name ?? "Attachment")
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            fallbackMediaPreview(type: type, name: media.name ?? "Attachment")
                .onTapGesture {
                    if let raw = media.url, let url = URL(string: raw) {
                        openURL(url)
                    }
                }
        }
    }

    @ViewBuilder
    private func fallbackMediaPreview(type: String, name: String) -> some View {
        let icon = type == "video" ? "video.fill" : "doc.fill"
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 30, weight: .semibold))
            Text(name)
                .font(.caption)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .padding(.horizontal, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .tertiarySystemFill))
    }

    private func normalizedAdMedia(_ ad: AdvertisementRecord) -> [GarageSaleMediaRecord] {
        if let media = ad.media, !media.isEmpty {
            return media
        }
        return (ad.mediaUrls ?? []).map {
            GarageSaleMediaRecord(url: $0, type: "image", name: "Image")
        }
    }

    private func toggleExpanded(id: String, ads: Bool) {
        if ads {
            if expandedAdIds.contains(id) {
                expandedAdIds.remove(id)
            } else {
                expandedAdIds.insert(id)
            }
            return
        }

        if expandedGarageIds.contains(id) {
            expandedGarageIds.remove(id)
        } else {
            expandedGarageIds.insert(id)
        }
    }

    private func telURL(from raw: String) -> URL? {
        let digits = raw.filter { "0123456789+".contains($0) }
        guard !digits.isEmpty else { return nil }
        return URL(string: "tel://\(digits)")
    }

    private func emailURL(from raw: String) -> URL? {
        let email = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !email.isEmpty else { return nil }
        return URL(string: "mailto:\(email)")
    }

    private func addressLine(for sale: GarageSaleRecord) -> String {
        [sale.address, sale.city, sale.state, sale.postalCode]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
    }

    private func mapURL(for sale: GarageSaleRecord) -> URL? {
        if let lat = sale.latitude, let lng = sale.longitude {
            return URL(string: "http://maps.apple.com/?ll=\(lat),\(lng)")
        }
        let address = addressLine(for: sale)
        guard !address.isEmpty else { return nil }
        let encoded = address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? address
        return URL(string: "http://maps.apple.com/?q=\(encoded)")
    }

}

private struct GarageSalePaymentSheet: View {
    let saleTitle: String
    @Binding var amountText: String
    let onCancel: () -> Void
    let onPayNow: () -> Void

    private var amount: Double {
        Double(amountText.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
    }

    private var platformFee: Double { amount * 0.02 }
    private var sellerNet: Double { max(0, amount - platformFee) }

    var body: some View {
        Form {
            Section("Garage Sale") {
                Text(saleTitle)
                    .font(.headline)
            }

            Section("Payment Amount") {
                TextField("Total sales amount", text: $amountText)
                    .keyboardType(.decimalPad)
            }

            if amount > 0 {
                Section("Breakdown") {
                    HStack {
                        Text("Platform fee (2%)")
                        Spacer()
                        Text(currency(platformFee))
                    }
                    HStack {
                        Text("Seller net")
                        Spacer()
                        Text(currency(sellerNet))
                    }
                }
            }
        }
        .navigationTitle("Pay Garage Sale")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Cancel") { onCancel() }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Pay Now") { onPayNow() }
                    .disabled(amount <= 0)
            }
        }
    }

    private func currency(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = "USD"
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "$%.2f", value)
    }
}
