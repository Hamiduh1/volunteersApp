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
            VStack(alignment: .leading, spacing: 24) {
                Text("Garage Sale Media")
                    .font(.title3.weight(.bold))
                mediaSection

                Text("Garage Sale Details")
                    .font(.title3.weight(.bold))
                basicSection

                Text("Contact Information")
                    .font(.title3.weight(.bold))
                contactSection

                Text("Address & Map")
                    .font(.title3.weight(.bold))
                addressSection

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
                            .fontWeight(.black)
                    }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .disabled(!isFormValid || viewModel.isSubmittingGarageSale)
            }
            .padding(20)
        }
        .navigationTitle("Create Garage Sale")
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
    private var mediaAddButtons: some View {
        PhotosPicker(
            selection: $imageItems,
            maxSelectionCount: 10,
            matching: .images
        ) {
            mediaAddCard(icon: "photo.fill", title: "Add Photos")
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
    private var basicSection: some View {
        VStack(spacing: 14) {
            TextField("Sale Title", text: $title)
                .textFieldStyle(.roundedBorder)
            TextField("Description", text: $description, axis: .vertical)
                .lineLimit(4...6)
                .textFieldStyle(.roundedBorder)
        }
    }

    @ViewBuilder
    private var contactSection: some View {
        VStack(spacing: 14) {
            TextField("Contact Name", text: $contactName)
                .textFieldStyle(.roundedBorder)
            TextField("Phone", text: $contactPhone)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.phonePad)
            TextField("Email", text: $contactEmail)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.emailAddress)
        }
    }

    @ViewBuilder
    private var addressSection: some View {
        VStack(spacing: 14) {
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
