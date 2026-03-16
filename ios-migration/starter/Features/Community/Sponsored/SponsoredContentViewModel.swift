import Foundation
import Combine

@MainActor
final class SponsoredContentViewModel: ObservableObject {
    @Published private(set) var advertisements: [AdvertisementRecord] = []
    @Published private(set) var garageSales: [GarageSaleRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = CommunityRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let adsTask = repository.fetchAdvertisements()
            async let garageTask = repository.fetchGarageSales()
            advertisements = try await adsTask
            garageSales = try await garageTask
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
