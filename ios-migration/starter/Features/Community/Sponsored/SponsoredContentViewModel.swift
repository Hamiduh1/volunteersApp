import Foundation
import Combine

@MainActor
final class SponsoredContentViewModel: ObservableObject {
    @Published private(set) var advertisements: [AdvertisementRecord] = []
    @Published private(set) var garageSales: [GarageSaleRecord] = []
    @Published var isLoading = false
    @Published var isSubmittingAd = false
    @Published var isSubmittingGarageSale = false
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

    @discardableResult
    func createAdvertisement(
        user: AppSessionUser,
        title: String,
        description: String,
        targetUrl: String,
        ownerPhone: String,
        media: [CommunityAttachmentDraft]
    ) async -> Bool {
        isSubmittingAd = true
        errorMessage = nil
        statusMessage = nil
        defer { isSubmittingAd = false }

        do {
            try await repository.createAdvertisement(
                user: user,
                title: title,
                description: description,
                targetUrl: targetUrl,
                ownerPhone: ownerPhone,
                media: media
            )
            await refresh()
            statusMessage = "Advertisement published."
            return true
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            return false
        }
    }

    @discardableResult
    func createGarageSale(
        user: AppSessionUser,
        title: String,
        description: String,
        contactName: String,
        contactPhone: String,
        contactEmail: String,
        address: String,
        city: String,
        state: String,
        postalCode: String,
        latitude: Double?,
        longitude: Double?,
        media: [CommunityAttachmentDraft]
    ) async -> Bool {
        isSubmittingGarageSale = true
        errorMessage = nil
        statusMessage = nil
        defer { isSubmittingGarageSale = false }

        do {
            try await repository.createGarageSale(
                user: user,
                title: title,
                description: description,
                contactName: contactName,
                contactPhone: contactPhone,
                contactEmail: contactEmail,
                address: address,
                city: city,
                state: state,
                postalCode: postalCode,
                latitude: latitude,
                longitude: longitude,
                media: media
            )
            await refresh()
            statusMessage = "Garage sale published."
            return true
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            return false
        }
    }
}
