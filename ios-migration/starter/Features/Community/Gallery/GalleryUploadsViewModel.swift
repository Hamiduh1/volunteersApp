import Foundation
import Combine

@MainActor
final class GalleryUploadsViewModel: ObservableObject {
    @Published private(set) var uploads: [GalleryUploadRecord] = []
    @Published var eventName = ""
    @Published var isLoading = false
    @Published var isUploading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = GalleryRepository()

    var canUpload: Bool {
        !isUploading && !eventName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            uploads = try await repository.fetchUploads()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func upload(user: AppSessionUser, imageData: Data) async {
        guard canUpload else { return }

        isUploading = true
        errorMessage = nil
        statusMessage = nil
        defer { isUploading = false }

        do {
            let name = eventName.trimmingCharacters(in: .whitespacesAndNewlines)
            try await repository.uploadImage(user: user, eventName: name, imageData: imageData)
            eventName = ""
            await refresh()
            statusMessage = "Image uploaded to gallery."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
