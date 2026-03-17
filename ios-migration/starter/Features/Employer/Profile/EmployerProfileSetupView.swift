import SwiftUI
import PhotosUI
import UIKit

struct EmployerProfileSetupView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = EmployerProfileSetupViewModel()
    @State private var selectedPhotoItem: PhotosPickerItem?

    var body: some View {
        Form {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }
            }

            Section {
                HStack(spacing: 14) {
                    avatarView
                        .frame(width: 72, height: 72)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(viewModel.organizationName.isEmpty ? "Organization Profile" : viewModel.organizationName)
                            .font(.headline)
                        Text(viewModel.contactEmail.isEmpty ? (viewModel.email.isEmpty ? (user.email ?? "") : viewModel.email) : viewModel.contactEmail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)

                PhotosPicker(
                    selection: $selectedPhotoItem,
                    matching: .images,
                    preferredItemEncoding: .automatic
                ) {
                    if viewModel.isUploadingImage {
                        HStack {
                            ProgressView()
                            Text("Uploading image...")
                        }
                    } else {
                        Label("Change Organization Image", systemImage: "photo")
                    }
                }
            }

            Section("Account") {
                TextField("Name", text: $viewModel.name)
                TextField("Email", text: $viewModel.email)
                    .disabled(true)
                    .foregroundStyle(.secondary)
            }

            Section("Organization") {
                TextField("Organization Name", text: $viewModel.organizationName)
                TextField("Contact Email", text: $viewModel.contactEmail)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.emailAddress)
                TextField("Description", text: $viewModel.description, axis: .vertical)
                    .lineLimit(3...6)
            }

            Section {
                Button {
                    Task { await viewModel.save(uid: user.uid) }
                } label: {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Text("Save Employer Profile")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isSaving)
            }
        }
        .navigationTitle("Employer Profile")
        .task { await viewModel.refresh(uid: user.uid) }
        .refreshable { await viewModel.refresh(uid: user.uid) }
        .onChange(of: selectedPhotoItem) { newValue in
            guard let newValue else { return }
            Task {
                if let data = try? await newValue.loadTransferable(type: Data.self) {
                    let uploadData: Data
                    if let image = UIImage(data: data), let jpeg = image.jpegData(compressionQuality: 0.85) {
                        uploadData = jpeg
                    } else {
                        uploadData = data
                    }
                    await viewModel.uploadProfileImage(uid: user.uid, jpegData: uploadData)
                }
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

    @ViewBuilder
    private var avatarView: some View {
        if let urlString = viewModel.profileImageUrl, let url = URL(string: urlString), !urlString.isEmpty {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .empty:
                    ZStack {
                        Circle().fill(Color.gray.opacity(0.25))
                        ProgressView()
                    }
                default:
                    ZStack {
                        Circle().fill(Color.gray.opacity(0.2))
                        Image(systemName: "building.2.fill")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .clipShape(Circle())
        } else {
            ZStack {
                Circle().fill(Color.gray.opacity(0.2))
                Image(systemName: "building.2.fill")
                    .foregroundStyle(.secondary)
            }
        }
    }
}
