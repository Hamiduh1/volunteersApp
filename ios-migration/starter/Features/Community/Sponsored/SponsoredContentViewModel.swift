import Foundation
import Combine

@MainActor
final class SponsoredContentViewModel: ObservableObject {
    @Published private(set) var advertisements: [AdvertisementRecord] = []
    @Published private(set) var garageSales: [GarageSaleRecord] = []
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = CommunityRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do { advertisements = try await repository.fetchAdvertisements() }
        catch {
            advertisements = []
            statusMessage = "Some sponsored data could not be loaded."
        }

        do { garageSales = try await repository.fetchGarageSales() }
        catch {
            garageSales = []
            statusMessage = "Some sponsored data could not be loaded."
        }

        if advertisements.isEmpty && garageSales.isEmpty {
            errorMessage = "Sponsored content is unavailable right now."
        }
    }
}
