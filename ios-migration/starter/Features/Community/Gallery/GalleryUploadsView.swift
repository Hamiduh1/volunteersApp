import SwiftUI
import PhotosUI
import UIKit

struct GalleryUploadsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = GalleryUploadsViewModel()
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedImageData: Data?

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Upload") {
                TextField("Event name", text: $viewModel.eventName)
                PhotosPicker(
                    selection: $selectedPhotoItem,
                    matching: .images
                ) {
                    Label(selectedImageData == nil ? "Select Image" : "Change Image", systemImage: "photo")
                }

                if let selectedImageData, let image = UIImage(data: selectedImageData) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Button {
                    guard let selectedImageData else { return }
                    Task { await viewModel.upload(user: user, imageData: selectedImageData) }
                } label: {
                    if viewModel.isUploading {
                        ProgressView()
                    } else {
                        Text("Upload to Gallery")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canUpload || selectedImageData == nil)
            }

            Section("Recent Uploads") {
                if viewModel.isLoading && viewModel.uploads.isEmpty {
                    ProgressView("Loading gallery...")
                } else if viewModel.uploads.isEmpty {
                    Text("No uploads yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.uploads) { upload in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top) {
                                AsyncImage(url: URL(string: upload.imageUrl ?? "")) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image.resizable().scaledToFill()
                                    case .empty:
                                        ProgressView()
                                    default:
                                        Image(systemName: "photo")
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .frame(width: 72, height: 72)
                                .clipShape(RoundedRectangle(cornerRadius: 10))

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(upload.name ?? "Untitled event")
                                        .font(.headline)
                                    if let ts = upload.timestamp?.dateValue() {
                                        Text(ts.formatted(date: .abbreviated, time: .shortened))
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    if let uploader = upload.uploaderId, !uploader.isEmpty {
                                        Text("By: \(uploader)")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Gallery Uploads")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
        .onChange(of: selectedPhotoItem) { newItem in
            guard let newItem else { return }
            Task {
                selectedImageData = try? await newItem.loadTransferable(type: Data.self)
            }
        }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }
}
