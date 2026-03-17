import Foundation
import Combine

@MainActor
final class MarketplaceViewModel: ObservableObject {
    @Published private(set) var items: [MarketplaceItemRecord] = []
    @Published var selectedCategory: String = "All"
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
        if selectedCategory == "All" { return items }
        return items.filter { ($0.category ?? "").caseInsensitiveCompare(selectedCategory) == .orderedSame }
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchMarketplaceItems()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
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
            await refresh()
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
            await refresh()
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
}
