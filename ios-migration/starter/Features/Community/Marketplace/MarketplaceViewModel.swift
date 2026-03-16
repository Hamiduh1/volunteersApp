import Foundation
import Combine

@MainActor
final class MarketplaceViewModel: ObservableObject {
    @Published private(set) var items: [MarketplaceItemRecord] = []
    @Published var selectedCategory: String = "All"
    @Published var title = ""
    @Published var description = ""
    @Published var category = "Other"
    @Published var price = ""
    @Published var isLoading = false
    @Published var isPosting = false
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
        defer { isLoading = false }

        do {
            items = try await repository.fetchMarketplaceItems()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func post(user: AppSessionUser) async {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDesc = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty, !cleanDesc.isEmpty else {
            errorMessage = "Title and description are required."
            return
        }
        guard let priceValue = Double(price), priceValue > 0 else {
            errorMessage = "Enter a valid price."
            return
        }

        isPosting = true
        errorMessage = nil
        defer { isPosting = false }

        do {
            try await repository.createMarketplaceItem(
                user: user,
                title: cleanTitle,
                description: cleanDesc,
                category: category,
                price: priceValue
            )
            title = ""
            description = ""
            category = "Other"
            price = ""
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
