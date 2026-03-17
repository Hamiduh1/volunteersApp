import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

struct CreateAdvertisementView: View {
    let user: AppSessionUser
    @ObservedObject var viewModel: SponsoredContentViewModel
    let onClose: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var targetUrl = ""
    @State private var ownerPhone = ""
    @State private var attachments: [CommunityAttachmentDraft] = []

    @State private var imageItems: [PhotosPickerItem] = []
    @State private var videoItems: [PhotosPickerItem] = []
    @State private var showDocImporter = false

    private var isFormValid: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !targetUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !attachments.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Visuals & Attachments")
                    .font(.title3.weight(.bold))

                mediaButtons

                if attachments.isEmpty {
                    Text("No media selected yet.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    attachmentPreviewList
                }

                Text("Ad Content")
                    .font(.title3.weight(.bold))

                TextField("Ad Title", text: $title)
                    .textFieldStyle(.roundedBorder)

                TextField("Detailed Description", text: $description, axis: .vertical)
                    .lineLimit(3...6)
                    .textFieldStyle(.roundedBorder)

                TextField("Target URL / Link", text: $targetUrl)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.URL)

                TextField("Contact Phone Number", text: $ownerPhone)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.phonePad)

                Button {
                    Task {
                        let success = await viewModel.createAdvertisement(
                            user: user,
                            title: title,
                            description: description,
                            targetUrl: targetUrl,
                            ownerPhone: ownerPhone,
                            media: attachments
                        )
                        if success { onClose() }
                    }
                } label: {
                    if viewModel.isSubmittingAd {
                        ProgressView()
                    } else {
                        Text("PUBLISH AD")
                            .fontWeight(.bold)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!isFormValid || viewModel.isSubmittingAd)
            }
            .padding(20)
        }
        .navigationTitle("Create Ad")
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
    private var mediaButtons: some View {
        HStack(spacing: 8) {
            PhotosPicker(
                selection: $imageItems,
                maxSelectionCount: 10,
                matching: .images
            ) {
                Label("Add Images", systemImage: "photo")
            }
            .buttonStyle(.bordered)

            PhotosPicker(
                selection: $videoItems,
                maxSelectionCount: 10,
                matching: .videos
            ) {
                Label("Add Videos", systemImage: "video")
            }
            .buttonStyle(.bordered)

            Button {
                showDocImporter = true
            } label: {
                Label("Add Docs", systemImage: "doc")
            }
            .buttonStyle(.bordered)
        }
    }

    @ViewBuilder
    private var attachmentPreviewList: some View {
        ForEach(attachments) { attachment in
            HStack(spacing: 10) {
                if attachment.type == .image, let uiImage = UIImage(data: attachment.data) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                } else if attachment.type == .video {
                    Image(systemName: "video")
                        .frame(width: 28)
                } else {
                    Image(systemName: "doc")
                        .frame(width: 28)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(attachment.fileName)
                        .font(.subheadline)
                        .lineLimit(2)
                    Text(attachment.type.rawValue.capitalized)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("Remove", role: .destructive) {
                    attachments.removeAll { $0.id == attachment.id }
                }
                .buttonStyle(.bordered)
            }
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
