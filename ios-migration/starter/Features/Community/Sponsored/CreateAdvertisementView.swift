import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

struct CreateAdvertisementView: View {
    let user: AppSessionUser
    @ObservedObject var viewModel: SponsoredContentViewModel
    let existingAd: AdvertisementRecord?
    let onClose: () -> Void

    @State private var title: String
    @State private var description: String
    @State private var targetUrl: String
    @State private var ownerPhone: String
    @State private var attachments: [CommunityAttachmentDraft] = []

    @State private var imageItems: [PhotosPickerItem] = []
    @State private var videoItems: [PhotosPickerItem] = []
    @State private var showDocImporter = false

    init(
        user: AppSessionUser,
        viewModel: SponsoredContentViewModel,
        existingAd: AdvertisementRecord? = nil,
        onClose: @escaping () -> Void
    ) {
        self.user = user
        self.viewModel = viewModel
        self.existingAd = existingAd
        self.onClose = onClose
        _title = State(initialValue: existingAd?.title ?? "")
        _description = State(initialValue: existingAd?.description ?? "")
        _targetUrl = State(initialValue: existingAd?.targetUrl ?? "")
        _ownerPhone = State(initialValue: existingAd?.ownerPhone ?? "")
    }

    private var existingMedia: [GarageSaleMediaRecord] {
        guard let existingAd else { return [] }
        if let media = existingAd.media, !media.isEmpty {
            return media
        }
        let urls = existingAd.mediaUrls ?? []
        return urls.map { GarageSaleMediaRecord(url: $0, type: "image", name: "Image") }
    }

    private var isFormValid: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !targetUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && (!attachments.isEmpty || !existingMedia.isEmpty)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Visuals & Attachments")
                    .font(.title3.weight(.bold))
                mediaSection

                Text("Ad Content")
                    .font(.title3.weight(.bold))
                contentSection

                Button {
                    Task {
                        let success: Bool
                        if let adId = existingAd?.id, !adId.isEmpty {
                            success = await viewModel.updateAdvertisement(
                                user: user,
                                adId: adId,
                                title: title,
                                description: description,
                                targetUrl: targetUrl,
                                ownerPhone: ownerPhone,
                                media: attachments
                            )
                        } else {
                            success = await viewModel.createAdvertisement(
                                user: user,
                                title: title,
                                description: description,
                                targetUrl: targetUrl,
                                ownerPhone: ownerPhone,
                                media: attachments
                            )
                        }
                        if success { onClose() }
                    }
                } label: {
                    if viewModel.isSubmittingAd {
                        ProgressView()
                    } else {
                        Text(existingAd == nil ? "PUBLISH AD" : "UPDATE AD")
                            .fontWeight(.black)
                    }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .disabled(!isFormValid || viewModel.isSubmittingAd)
            }
            .padding(20)
        }
        .navigationTitle(existingAd == nil ? "Create Ad" : "Edit Ad")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Close") { onClose() }
            }
        }
        .onChange(of: imageItems) { _, items in
            Task {
                await appendPhotoAttachments(from: items, type: .image)
                imageItems = []
            }
        }
        .onChange(of: videoItems) { _, items in
            Task {
                await appendPhotoAttachments(from: items, type: .video)
                videoItems = []
            }
        }
        .fileImporter(
            isPresented: $showDocImporter,
            allowedContentTypes: [.pdf, .item],
            allowsMultipleSelection: true
        ) { result in
            guard case .success(let urls) = result else { return }
            for url in urls {
                let granted = url.startAccessingSecurityScopedResource()
                defer {
                    if granted { url.stopAccessingSecurityScopedResource() }
                }

                do {
                    let data = try Data(contentsOf: url)
                    attachments.append(
                        CommunityAttachmentDraft(
                            type: .document,
                            data: data,
                            fileName: url.lastPathComponent,
                            contentType: "application/pdf"
                        )
                    )
                } catch {
                    viewModel.errorMessage = AppErrorMapper.message(from: error)
                }
            }
        }
    }

    @ViewBuilder
    private var mediaSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                if attachments.isEmpty {
                    ForEach(Array(existingMedia.enumerated()), id: \.offset) { _, media in
                        existingMediaAttachmentCard(media)
                    }
                }
                ForEach(attachments) { attachment in
                    mediaAttachmentCard(attachment)
                }
                mediaAddButtons
            }
            .padding(.horizontal, 2)
        }
        .frame(height: 170)
    }

    @ViewBuilder
    private func existingMediaAttachmentCard(_ media: GarageSaleMediaRecord) -> some View {
        ZStack {
            if (media.type ?? "").lowercased() == "image",
               let raw = media.url,
               let url = URL(string: raw) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .empty:
                        ProgressView()
                    default:
                        fallbackExistingMediaCard(media: media)
                    }
                }
            } else {
                fallbackExistingMediaCard(media: media)
            }
        }
        .frame(width: 160, height: 160)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private func fallbackExistingMediaCard(media: GarageSaleMediaRecord) -> some View {
        VStack(spacing: 8) {
            Image(systemName: (media.type ?? "").lowercased() == "video" ? "video.fill" : "doc.fill")
                .font(.system(size: 30, weight: .semibold))
            Text((media.name ?? "").isEmpty ? "Attachment" : (media.name ?? "Attachment"))
                .font(.caption2)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .padding(.horizontal, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .secondarySystemBackground))
    }

    @ViewBuilder
    private var mediaAddButtons: some View {
        PhotosPicker(
            selection: $imageItems,
            maxSelectionCount: 10,
            matching: .images
        ) {
            mediaAddCard(icon: "photo.fill", title: "Add Images")
        }
        .buttonStyle(.plain)

        PhotosPicker(
            selection: $videoItems,
            maxSelectionCount: 10,
            matching: .videos
        ) {
            mediaAddCard(icon: "video.fill", title: "Add Videos")
        }
        .buttonStyle(.plain)

        Button {
            showDocImporter = true
        } label: {
            mediaAddCard(icon: "doc.fill", title: "Add Docs")
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func mediaAttachmentCard(_ attachment: CommunityAttachmentDraft) -> some View {
        ZStack(alignment: .topTrailing) {
            Group {
                if attachment.type == .image, let uiImage = UIImage(data: attachment.data) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                } else {
                    VStack(spacing: 8) {
                        Image(systemName: attachment.type == .video ? "video.fill" : "doc.fill")
                            .font(.system(size: 30, weight: .semibold))
                        Text(attachment.fileName)
                            .font(.caption2)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(uiColor: .secondarySystemBackground))
                }
            }
            .frame(width: 160, height: 160)
            .clipShape(RoundedRectangle(cornerRadius: 16))

            Button {
                attachments.removeAll { $0.id == attachment.id }
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(6)
                    .background(Color.black.opacity(0.7), in: Circle())
            }
            .padding(8)
        }
    }

    @ViewBuilder
    private func mediaAddCard(icon: String, title: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 30, weight: .semibold))
            Text(title)
                .font(.caption)
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
        }
        .frame(width: 160, height: 160)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private var contentSection: some View {
        VStack(spacing: 14) {
            TextField("Ad Title", text: $title)
                .textFieldStyle(.roundedBorder)
            TextField("Detailed Description", text: $description, axis: .vertical)
                .lineLimit(4...6)
                .textFieldStyle(.roundedBorder)
            TextField("Target URL / Link", text: $targetUrl)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.URL)
            TextField("Contact Phone Number", text: $ownerPhone)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.phonePad)
        }
    }

    private func appendPhotoAttachments(from items: [PhotosPickerItem], type: CommunityAttachmentType) async {
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
            let now = Int(Date().timeIntervalSince1970 * 1000)
            let ext = type == .image ? "jpg" : "mp4"
            let contentType = type == .image ? "image/jpeg" : "video/mp4"
            let name = "ad_\(type.rawValue)_\(now).\(ext)"
            attachments.append(
                CommunityAttachmentDraft(
                    type: type,
                    data: data,
                    fileName: name,
                    contentType: contentType
                )
            )
        }
    }
}
