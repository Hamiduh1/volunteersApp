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
    func updateAdvertisement(
        user: AppSessionUser,
        adId: String,
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
            try await repository.updateAdvertisement(
                user: user,
                adId: adId,
                title: title,
                description: description,
                targetUrl: targetUrl,
                ownerPhone: ownerPhone,
                newMedia: media
            )
            await refresh()
            statusMessage = "Advertisement updated."
            return true
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            return false
        }
    }

    func deleteAdvertisement(adId: String) async {
        errorMessage = nil
        statusMessage = nil
        do {
            try await repository.deleteAdvertisement(adId: adId)
            await refresh()
            statusMessage = "Ad deleted successfully."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
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

    func submitGarageSalePayment(
        buyer: AppSessionUser,
        sellerId: String,
        garageSaleId: String,
        amount: Double
    ) async {
        errorMessage = nil
        statusMessage = nil
        do {
            try await repository.submitGarageSalePayment(
                buyerUid: buyer.uid,
                sellerUid: sellerId,
                garageSaleId: garageSaleId,
                amount: amount
            )
            statusMessage = "Payment submitted. Processing now."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func sendAdChatInvitation(user: AppSessionUser, ad: AdvertisementRecord) async {
        errorMessage = nil
        statusMessage = nil
        let ownerId = (ad.ownerId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !ownerId.isEmpty else {
            errorMessage = "Recipient info missing."
            return
        }

        do {
            let message = try await repository.sendSponsoredChatInvitation(
                sender: user,
                recipientId: ownerId,
                contextLabel: "Ad: \(ad.title ?? "Advertisement")",
                duplicateMessage: "Invitation already sent to advertiser!"
            )
            statusMessage = message
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func sendGarageSaleChatInvitation(user: AppSessionUser, sale: GarageSaleRecord) async {
        errorMessage = nil
        statusMessage = nil
        let ownerId = (sale.ownerId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !ownerId.isEmpty else {
            errorMessage = "Recipient info missing."
            return
        }

        do {
            let message = try await repository.sendSponsoredChatInvitation(
                sender: user,
                recipientId: ownerId,
                contextLabel: "Garage Sale: \(sale.title ?? "Listing")",
                duplicateMessage: "Invitation already sent to seller!"
            )
            statusMessage = message
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
