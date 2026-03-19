import Foundation
import SwiftUI
import PhotosUI
import UIKit
import CoreLocation

struct MarketplaceView: View {
    private enum SectionAnchor {
        static let dashboard = "marketplace_dashboard"
        static let filter = "marketplace_filter"
        static let search = "marketplace_search"
        static let listings = "marketplace_listings"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = MarketplaceViewModel()
    @StateObject private var locationRequester = MarketplaceLocationRequester()
    @State private var showCreateSheet = false
    @State private var editingItem: MarketplaceItemRecord?
    @State private var pendingDeleteItem: MarketplaceItemRecord?
    @State private var pendingBuyItem: MarketplaceItemRecord?
    @State private var expandedItemIds: Set<String> = []
    @State private var hasRequestedLocation = false
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

                Section("Filter") {
                    Picker("Category", selection: $viewModel.selectedCategory) {
                        ForEach(viewModel.categories, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    .pickerStyle(.menu)
                }
                .id(SectionAnchor.filter)

                Section("Search") {
                    TextField("Search listings", text: $viewModel.searchQuery)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                .id(SectionAnchor.search)

                Section("Listings") {
                    if viewModel.isLoading && viewModel.filteredItems.isEmpty {
                        ProgressView("Loading marketplace...")
                    } else if viewModel.filteredItems.isEmpty {
                        Text("No listings yet.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.filteredItems) { item in
                            listingCard(item)
                                .listRowInsets(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
                                .listRowSeparator(.hidden)
                        }
                    }
                }
                .id(SectionAnchor.listings)
            }
            .listStyle(.plain)
        }
        .navigationTitle("Marketplace")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    locationRequester.requestLocation()
                } label: {
                    Image(systemName: "location")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    AIAssistantView()
                } label: {
                    Image(systemName: "sparkles")
                }
            }
        }
        .overlay(alignment: .bottomTrailing) {
            Button {
                showCreateSheet = true
            } label: {
                Label("Post Item", systemImage: "plus")
                    .fontWeight(.semibold)
            }
            .buttonStyle(.borderedProminent)
            .padding(.trailing, 16)
            .padding(.bottom, 22)
        }
        .sheet(isPresented: $showCreateSheet) {
            NavigationStack {
                MarketplaceCreateSheet(user: user, viewModel: viewModel) {
                    showCreateSheet = false
                }
            }
        }
        .sheet(isPresented: Binding(
            get: { editingItem != nil },
            set: { if !$0 { editingItem = nil } }
        )) {
            if let item = editingItem {
                NavigationStack {
                    MarketplaceEditSheet(user: user, viewModel: viewModel, item: item) {
                        editingItem = nil
                    }
                }
            }
        }
        .confirmationDialog("Delete Item", isPresented: Binding(
            get: { pendingDeleteItem != nil },
            set: { if !$0 { pendingDeleteItem = nil } }
        ), titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                guard let itemId = pendingDeleteItem?.id, !itemId.isEmpty else {
                    pendingDeleteItem = nil
                    return
                }
                Task {
                    await viewModel.delete(itemId: itemId)
                    pendingDeleteItem = nil
                }
            }
            Button("Cancel", role: .cancel) { pendingDeleteItem = nil }
        }
        .alert("Confirm Purchase", isPresented: Binding(
            get: { pendingBuyItem != nil },
            set: { if !$0 { pendingBuyItem = nil } }
        )) {
            Button("Cancel", role: .cancel) { pendingBuyItem = nil }
            Button("Confirm") {
                guard let item = pendingBuyItem else { return }
                Task {
                    await viewModel.buy(user: user, item: item)
                    pendingBuyItem = nil
                }
            }
        } message: {
            if let item = pendingBuyItem {
                let price = item.price ?? 0
                let fee = price * 0.02
                let sellerNet = max(0, price - fee)
                Text("""
                You're about to buy "\(item.title ?? "this item")".
                Item price: \(currency(price))
                Platform fee (2%): \(currency(fee))
                Seller receives: \(currency(sellerNet))
                """)
            } else {
                Text("You're about to buy this item.")
            }
        }
        .task {
            await viewModel.refresh(user: user)
            if !hasRequestedLocation {
                hasRequestedLocation = true
                locationRequester.requestLocation()
            }
        }
        .refreshable { await viewModel.refresh(user: user) }
        .onReceive(locationRequester.$latestCoordinate) { coordinate in
            guard let coordinate else { return }
            viewModel.updateCurrentUserLocation(
                latitude: coordinate.latitude,
                longitude: coordinate.longitude
            )
        }
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

            Text("Android-style marketplace control center and quick navigation.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                dashboardMetric(value: "\(viewModel.items.count)", label: "Total")
                dashboardMetric(value: "\(viewModel.filteredItems.count)", label: "Visible")
                dashboardMetric(value: "\(max(viewModel.categories.count - 1, 0))", label: "Categories")
            }

            HStack(spacing: 10) {
                dashboardTile(title: "Post Item", icon: "plus.circle.fill") {
                    showCreateSheet = true
                }
                dashboardTile(title: "Refresh", icon: "arrow.clockwise.circle.fill") {
                    Task { await viewModel.refresh(user: user) }
                }
                dashboardTile(title: "Locate", icon: "location.circle.fill") {
                    locationRequester.requestLocation()
                }
            }

            HStack(spacing: 8) {
                dashboardQuickButton("Filter") {
                    withAnimation { proxy.scrollTo(SectionAnchor.filter, anchor: .top) }
                }
                dashboardQuickButton("Search") {
                    withAnimation { proxy.scrollTo(SectionAnchor.search, anchor: .top) }
                }
                dashboardQuickButton("Listings") {
                    withAnimation { proxy.scrollTo(SectionAnchor.listings, anchor: .top) }
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
    private func listingCard(_ item: MarketplaceItemRecord) -> some View {
        let itemId = item.id ?? "item-\(item.title ?? "untitled")-\(item.timestamp?.seconds ?? 0)"
        let isExpanded = expandedItemIds.contains(itemId)
        let isOwner = (item.sellerId ?? "") == user.uid
        let isBuying = viewModel.buyingItemIds.contains(item.id ?? "")
        let isDeleting = viewModel.deletingItemIds.contains(item.id ?? "")

        VStack(alignment: .leading, spacing: 10) {
            mediaPager(item: item)

            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title ?? "Untitled")
                        .font(.headline)
                        .lineLimit(1)
                    Text("Sold by \(item.sellerName ?? "Seller")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let location = item.locationName, !location.isEmpty {
                        let distance = viewModel.distanceText(for: item)
                        if let distance {
                            Text("\(location) • \(distance)")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        } else {
                            Text(location)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                Spacer()
                Text(currency(item.price ?? 0))
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.primary)
                Button {
                    toggleExpanded(itemId: itemId)
                } label: {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.callout.weight(.semibold))
                }
                .buttonStyle(.plain)
            }

            if isExpanded {
                if let description = item.description, !description.isEmpty {
                    Text(description)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Category: \(item.category ?? "Other")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let location = item.locationName, !location.isEmpty {
                        Text("Location: \(location)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let ts = item.timestamp?.dateValue() {
                        Text(ts.formatted(date: .abbreviated, time: .shortened))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                if isOwner {
                    HStack(spacing: 8) {
                        Button("Edit") { editingItem = item }
                            .buttonStyle(.bordered)
                        Button("Delete", role: .destructive) { pendingDeleteItem = item }
                            .buttonStyle(.bordered)
                    }
                } else {
                    HStack(spacing: 8) {
                        Button(isBuying ? "Processing..." : "Buy Now") {
                            pendingBuyItem = item
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isBuying)

                        Button("Chat") {
                            Task { await viewModel.sendChatInvitation(user: user, item: item) }
                        }
                        .buttonStyle(.bordered)

                        if let phone = item.sellerPhone, let tel = telURL(from: phone) {
                            Button("Call") { openURL(tel) }
                                .buttonStyle(.bordered)
                        }

                        if let map = mapURL(for: item) {
                            Button("Map") { openURL(map) }
                                .buttonStyle(.bordered)
                        }
                    }
                }
            }

            if isDeleting {
                ProgressView()
                    .controlSize(.small)
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(uiColor: .secondarySystemBackground))
        )
    }

    @ViewBuilder
    private func mediaPager(item: MarketplaceItemRecord) -> some View {
        let images = item.imageUrls ?? []
        if images.isEmpty {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(uiColor: .tertiarySystemFill))
                Image(systemName: "storefront")
                    .foregroundStyle(.secondary)
            }
            .frame(height: 180)
        } else {
            TabView {
                ForEach(Array(images.enumerated()), id: \.offset) { _, urlText in
                    if let url = URL(string: urlText) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let image):
                                image.resizable().scaledToFill()
                            case .empty:
                                ProgressView()
                            default:
                                Image(systemName: "photo")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    } else {
                        Image(systemName: "photo")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
            .frame(height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private func toggleExpanded(itemId: String) {
        if expandedItemIds.contains(itemId) {
            expandedItemIds.remove(itemId)
        } else {
            expandedItemIds.insert(itemId)
        }
    }

    private func telURL(from raw: String) -> URL? {
        let digits = raw.filter { "0123456789+".contains($0) }
        guard !digits.isEmpty else { return nil }
        return URL(string: "tel://\(digits)")
    }

    private func mapURL(for item: MarketplaceItemRecord) -> URL? {
        if let lat = item.latitude, let lng = item.longitude, !(lat == 0 && lng == 0) {
            return URL(string: "http://maps.apple.com/?ll=\(lat),\(lng)")
        }
        let location = (item.locationName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !location.isEmpty else { return nil }
        let encoded = location.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? location
        return URL(string: "http://maps.apple.com/?q=\(encoded)")
    }

    private func currency(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = "USD"
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "$%.2f", value)
    }
}

private struct MarketplaceCreateSheet: View {
    let user: AppSessionUser
    @ObservedObject var viewModel: MarketplaceViewModel
    let onClose: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var category = "Other"
    @State private var price = ""
    @State private var sellerPhone = ""
    @State private var locationName = ""
    @State private var latitude = ""
    @State private var longitude = ""
    @State private var imageItems: [PhotosPickerItem] = []
    @State private var imageDrafts: [CommunityAttachmentDraft] = []

    private var canPost: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && (Double(price.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0) > 0
        && !imageDrafts.isEmpty
        && !viewModel.isPosting
    }

    var body: some View {
        Form {
            Section("Item Details") {
                TextField("Title", text: $title)
                TextField("Description", text: $description, axis: .vertical)
                    .lineLimit(3...6)
                Picker("Category", selection: $category) {
                    ForEach(viewModel.categories.filter { $0 != "All" }, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
                TextField("Price (USD)", text: $price)
                    .keyboardType(.decimalPad)
                TextField("Seller Phone", text: $sellerPhone)
                    .keyboardType(.phonePad)
            }

            Section("Location") {
                TextField("Location Name", text: $locationName)
                TextField("Latitude (optional)", text: $latitude)
                    .keyboardType(.decimalPad)
                TextField("Longitude (optional)", text: $longitude)
                    .keyboardType(.decimalPad)
            }

            Section("Images (\(imageDrafts.count))") {
                PhotosPicker(selection: $imageItems, maxSelectionCount: 10, matching: .images) {
                    Label("Select Images", systemImage: "photo.on.rectangle")
                }
                ForEach(imageDrafts) { draft in
                    HStack {
                        if let image = UIImage(data: draft.data) {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 48, height: 48)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        } else {
                            Image(systemName: "photo")
                        }
                        Text(draft.fileName)
                            .lineLimit(1)
                        Spacer()
                        Button(role: .destructive) {
                            imageDrafts.removeAll { $0.id == draft.id }
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                        }
                    }
                }
            }
        }
        .navigationTitle("Post Item")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Close") { onClose() }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink {
                    AIAssistantView()
                } label: {
                    Image(systemName: "sparkles")
                }
                Button(viewModel.isPosting ? "Posting..." : "Post") {
                    Task {
                        let success = await viewModel.post(
                            user: user,
                            title: title,
                            description: description,
                            category: category,
                            price: Double(price.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                            sellerPhone: sellerPhone,
                            locationName: locationName,
                            latitude: Double(latitude.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                            longitude: Double(longitude.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                            images: imageDrafts
                        )
                        if success { onClose() }
                    }
                }
                .disabled(!canPost)
            }
        }
        .onChange(of: imageItems) { _, items in
            Task {
                var drafts = imageDrafts
                for item in items {
                    guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
                    let fileName = "marketplace_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
                    drafts.append(
                        CommunityAttachmentDraft(
                            type: .image,
                            data: data,
                            fileName: fileName,
                            contentType: "image/jpeg"
                        )
                    )
                }
                imageDrafts = Array(drafts.prefix(10))
                imageItems = []
            }
        }
    }
}

private struct MarketplaceEditSheet: View {
    let user: AppSessionUser
    @ObservedObject var viewModel: MarketplaceViewModel
    let item: MarketplaceItemRecord
    let onClose: () -> Void

    @State private var title: String
    @State private var description: String
    @State private var category: String
    @State private var price: String
    @State private var sellerPhone: String
    @State private var locationName: String
    @State private var latitude: String
    @State private var longitude: String
    @State private var imageItems: [PhotosPickerItem] = []
    @State private var newImageDrafts: [CommunityAttachmentDraft] = []
    @State private var existingImageUrls: [String]

    init(user: AppSessionUser, viewModel: MarketplaceViewModel, item: MarketplaceItemRecord, onClose: @escaping () -> Void) {
        self.user = user
        self.viewModel = viewModel
        self.item = item
        self.onClose = onClose
        _title = State(initialValue: item.title ?? "")
        _description = State(initialValue: item.description ?? "")
        _category = State(initialValue: (item.category ?? "Other").isEmpty ? "Other" : (item.category ?? "Other"))
        _price = State(initialValue: String(format: "%.2f", item.price ?? 0))
        _sellerPhone = State(initialValue: item.sellerPhone ?? "")
        _locationName = State(initialValue: item.locationName ?? "")
        _latitude = State(initialValue: item.latitude.map { String($0) } ?? "")
        _longitude = State(initialValue: item.longitude.map { String($0) } ?? "")
        _existingImageUrls = State(initialValue: item.imageUrls ?? [])
    }

    private var canSave: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && (Double(price.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0) > 0
        && (!existingImageUrls.isEmpty || !newImageDrafts.isEmpty)
        && !viewModel.updatingItemIds.contains(item.id ?? "")
    }

    var body: some View {
        Form {
            Section("Item Details") {
                TextField("Title", text: $title)
                TextField("Description", text: $description, axis: .vertical)
                    .lineLimit(3...6)
                Picker("Category", selection: $category) {
                    ForEach(viewModel.categories.filter { $0 != "All" }, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
                TextField("Price (USD)", text: $price)
                    .keyboardType(.decimalPad)
                TextField("Seller Phone", text: $sellerPhone)
                    .keyboardType(.phonePad)
            }

            Section("Location") {
                TextField("Location Name", text: $locationName)
                TextField("Latitude (optional)", text: $latitude)
                    .keyboardType(.decimalPad)
                TextField("Longitude (optional)", text: $longitude)
                    .keyboardType(.decimalPad)
            }

            Section("Existing Images (\(existingImageUrls.count))") {
                if existingImageUrls.isEmpty {
                    Text("No existing images.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(Array(existingImageUrls.enumerated()), id: \.offset) { _, urlText in
                        HStack {
                            AsyncImage(url: URL(string: urlText)) { phase in
                                switch phase {
                                case .success(let image):
                                    image.resizable().scaledToFill()
                                default:
                                    Image(systemName: "photo")
                                }
                            }
                            .frame(width: 48, height: 48)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            Text(urlText)
                                .lineLimit(1)
                            Spacer()
                            Button(role: .destructive) {
                                existingImageUrls.removeAll { $0 == urlText }
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                            }
                        }
                    }
                }
            }

            Section("New Images (\(newImageDrafts.count))") {
                PhotosPicker(selection: $imageItems, maxSelectionCount: 10, matching: .images) {
                    Label("Add More Images", systemImage: "photo.on.rectangle")
                }
                ForEach(newImageDrafts) { draft in
                    HStack {
                        if let image = UIImage(data: draft.data) {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 48, height: 48)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        } else {
                            Image(systemName: "photo")
                        }
                        Text(draft.fileName)
                            .lineLimit(1)
                        Spacer()
                        Button(role: .destructive) {
                            newImageDrafts.removeAll { $0.id == draft.id }
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                        }
                    }
                }
            }
        }
        .navigationTitle("Edit Listing")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Close") { onClose() }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink {
                    AIAssistantView()
                } label: {
                    Image(systemName: "sparkles")
                }
                Button(viewModel.updatingItemIds.contains(item.id ?? "") ? "Saving..." : "Save") {
                    Task {
                        let success = await viewModel.update(
                            user: user,
                            itemId: item.id ?? "",
                            title: title,
                            description: description,
                            category: category,
                            price: Double(price.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                            sellerPhone: sellerPhone,
                            locationName: locationName,
                            latitude: Double(latitude.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                            longitude: Double(longitude.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                            existingImageUrls: existingImageUrls,
                            newImages: newImageDrafts
                        )
                        if success { onClose() }
                    }
                }
                .disabled(!canSave)
            }
        }
        .onChange(of: imageItems) { _, items in
            Task {
                var drafts = newImageDrafts
                for item in items {
                    guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
                    let fileName = "marketplace_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
                    drafts.append(
                        CommunityAttachmentDraft(
                            type: .image,
                            data: data,
                            fileName: fileName,
                            contentType: "image/jpeg"
                        )
                    )
                }
                newImageDrafts = Array(drafts.prefix(10))
                imageItems = []
            }
        }
    }
}

private final class MarketplaceLocationRequester: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var latestCoordinate: CLLocationCoordinate2D?

    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestLocation() {
        guard CLLocationManager.locationServicesEnabled() else { return }

        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        case .denied, .restricted:
            return
        @unknown default:
            return
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        latestCoordinate = locations.last?.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Keep marketplace usable even when location fails or is blocked.
        print("Marketplace location request failed: \(error.localizedDescription)")
    }
}
