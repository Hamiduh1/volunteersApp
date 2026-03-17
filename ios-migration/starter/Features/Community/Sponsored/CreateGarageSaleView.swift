import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

struct CreateGarageSaleView: View {
    let user: AppSessionUser
    @ObservedObject var viewModel: SponsoredContentViewModel
    let onClose: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var contactName = ""
    @State private var contactPhone = ""
    @State private var contactEmail = ""
    @State private var address = ""
    @State private var city = ""
    @State private var state = ""
    @State private var postalCode = ""
    @State private var latitude = ""
    @State private var longitude = ""

    @State private var attachments: [CommunityAttachmentDraft] = []
    @State private var imageItems: [PhotosPickerItem] = []
    @State private var videoItems: [PhotosPickerItem] = []
    @State private var showDocImporter = false

    private var isFormValid: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !contactName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Garage Sale Media")
                    .font(.title3.weight(.bold))

                mediaButtons

                if attachments.isEmpty {
                    Text("No media selected yet.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    attachmentPreviewList
                }

                Text("Garage Sale Details")
                    .font(.title3.weight(.bold))

                TextField("Sale Title", text: $title)
                    .textFieldStyle(.roundedBorder)

                TextField("Description", text: $description, axis: .vertical)
                    .lineLimit(3...6)
                    .textFieldStyle(.roundedBorder)

                Text("Contact Information")
                    .font(.title3.weight(.bold))

                TextField("Contact Name", text: $contactName)
                    .textFieldStyle(.roundedBorder)

                TextField("Phone", text: $contactPhone)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.phonePad)

                TextField("Email", text: $contactEmail)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.emailAddress)

                Text("Address & Map")
                    .font(.title3.weight(.bold))

                TextField("Street Address", text: $address)
                    .textFieldStyle(.roundedBorder)
                TextField("City", text: $city)
                    .textFieldStyle(.roundedBorder)
                TextField("State", text: $state)
                    .textFieldStyle(.roundedBorder)
                TextField("Postal Code", text: $postalCode)
                    .textFieldStyle(.roundedBorder)

                TextField("Latitude (optional)", text: $latitude)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.decimalPad)
                TextField("Longitude (optional)", text: $longitude)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.decimalPad)

                Button {
                    Task {
                        let success = await viewModel.createGarageSale(
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
                            latitude: Double(latitude.trimmingCharacters(in: .whitespacesAndNewlines)),
                            longitude: Double(longitude.trimmingCharacters(in: .whitespacesAndNewlines)),
                            media: attachments
                        )
                        if success { onClose() }
                    }
                } label: {
                    if viewModel.isSubmittingGarageSale {
                        ProgressView()
                    } else {
                        Text("PUBLISH GARAGE SALE")
                            .fontWeight(.bold)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!isFormValid || viewModel.isSubmittingGarageSale)
            }
            .padding(20)
        }
        .navigationTitle("Create Garage Sale")
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
                Label("Add Photos", systemImage: "photo")
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
            let name = "garage_\(type.rawValue)_\(now).\(ext)"
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
