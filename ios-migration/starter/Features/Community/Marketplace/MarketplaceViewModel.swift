import Foundation
import Combine

@MainActor
final class MarketplaceViewModel: ObservableObject {
    @Published private(set) var items: [MarketplaceItemRecord] = []
    @Published var selectedCategory: String = "All"
    @Published var searchQuery: String = ""
    @Published private(set) var currentUserLocation: (Double, Double)?
    @Published private(set) var isLoading = false
    @Published private(set) var isPosting = false
    @Published private(set) var buyingItemIds: Set<String> = []
    @Published private(set) var deletingItemIds: Set<String> = []
    @Published private(set) var updatingItemIds: Set<String> = []
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = CommunityRepository()

    var categories: [String] {
        ["All", "Electronics", "Furniture", "Clothing", "Books", "Home Goods", "Other"]
    }

    var filteredItems: [MarketplaceItemRecord] {
        let categoryFiltered: [MarketplaceItemRecord]
        if selectedCategory == "All" {
            categoryFiltered = items
        } else {
            categoryFiltered = items.filter { ($0.category ?? "").caseInsensitiveCompare(selectedCategory) == .orderedSame }
        }

        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !query.isEmpty else { return categoryFiltered }
        return categoryFiltered.filter { item in
            let fields = [
                item.title ?? "",
                item.description ?? "",
                item.sellerName ?? "",
                item.category ?? "",
                item.locationName ?? ""
            ]
            return fields.joined(separator: " ").lowercased().contains(query)
        }
    }

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let itemsTask = repository.fetchMarketplaceItems()
            async let locationTask = repository.fetchUserLocation(uid: user.uid)
            items = try await itemsTask
            if let location = try await locationTask {
                currentUserLocation = location
            }
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func updateCurrentUserLocation(latitude: Double, longitude: Double) {
        currentUserLocation = (latitude, longitude)
    }

    func post(
        user: AppSessionUser,
        title: String,
        description: String,
        category: String,
        price: Double,
        sellerPhone: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        images: [CommunityAttachmentDraft]
    ) async -> Bool {
        isPosting = true
        errorMessage = nil
        statusMessage = nil
        defer { isPosting = false }

        do {
            try await repository.createMarketplaceItem(
                user: user,
                title: title,
                description: description,
                category: category,
                price: price,
                sellerPhone: sellerPhone,
                locationName: locationName,
                latitude: latitude,
                longitude: longitude,
                images: images
            )
            await refresh(user: user)
            statusMessage = "Item posted successfully."
            return true
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            return false
        }
    }

    func update(
        user: AppSessionUser,
        itemId: String,
        title: String,
        description: String,
        category: String,
        price: Double,
        sellerPhone: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        existingImageUrls: [String],
        newImages: [CommunityAttachmentDraft]
    ) async -> Bool {
        let cleanItemId = itemId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanItemId.isEmpty else {
            errorMessage = "Item ID is missing."
            return false
        }
        guard !updatingItemIds.contains(cleanItemId) else { return false }
        updatingItemIds.insert(cleanItemId)
        defer { updatingItemIds.remove(cleanItemId) }

        errorMessage = nil
        statusMessage = nil

        do {
            try await repository.updateMarketplaceItem(
                user: user,
                itemId: cleanItemId,
                title: title,
                description: description,
                category: category,
                price: price,
                sellerPhone: sellerPhone,
                locationName: locationName,
                latitude: latitude,
                longitude: longitude,
                existingImageUrls: existingImageUrls,
                newImages: newImages
            )
            await refresh(user: user)
            statusMessage = "Listing updated."
            return true
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            return false
        }
    }

    func delete(itemId: String) async {
        let cleanItemId = itemId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanItemId.isEmpty else { return }
        guard !deletingItemIds.contains(cleanItemId) else { return }
        deletingItemIds.insert(cleanItemId)
        defer { deletingItemIds.remove(cleanItemId) }

        errorMessage = nil
        statusMessage = nil
        do {
            try await repository.deleteMarketplaceItem(itemId: cleanItemId)
            items.removeAll { $0.id == cleanItemId }
            statusMessage = "Item removed."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func buy(user: AppSessionUser, item: MarketplaceItemRecord) async {
        let itemId = item.id ?? ""
        guard !itemId.isEmpty else { return }
        guard !buyingItemIds.contains(itemId) else { return }
        buyingItemIds.insert(itemId)
        defer { buyingItemIds.remove(itemId) }

        errorMessage = nil
        statusMessage = nil
        do {
            try await repository.submitMarketplacePurchaseRequest(buyerUid: user.uid, item: item)
            statusMessage = "Purchase request submitted. Processing now."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func sendChatInvitation(user: AppSessionUser, item: MarketplaceItemRecord) async {
        errorMessage = nil
        statusMessage = nil
        do {
            let message = try await repository.sendMarketplaceChatInvitation(sender: user, item: item)
            statusMessage = message
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func distanceText(for item: MarketplaceItemRecord) -> String? {
        guard let userLocation = currentUserLocation else { return nil }
        guard let lat = item.latitude, let lng = item.longitude else { return nil }
        if lat == 0 && lng == 0 { return nil }

        let distanceKm = calculateDistanceKm(
            lat1: userLocation.0,
            lon1: userLocation.1,
            lat2: lat,
            lon2: lng
        )
        if distanceKm < 1 {
            return "<1 km away"
        }
        return "\(roundToOneDecimal(distanceKm)) km away"
    }

    private func calculateDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ) -> Double {
        let earthRadiusKm = 6371.0
        let dLat = (lat2 - lat1) * Double.pi / 180.0
        let dLon = (lon2 - lon1) * Double.pi / 180.0
        let a =
            sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * Double.pi / 180.0) * cos(lat2 * Double.pi / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * asin(sqrt(a))
        return earthRadiusKm * c
    }

    private func roundToOneDecimal(_ value: Double) -> Double {
        (value * 10).rounded() / 10
    }
}
