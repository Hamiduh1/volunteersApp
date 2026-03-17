import SwiftUI

private enum SponsoredSection: String, CaseIterable, Identifiable {
    case ads = "Sponsored Ads"
    case garage = "Garage Sales"

    var id: String { rawValue }
}

struct SponsoredContentView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = SponsoredContentViewModel()
    @State private var selectedSection: SponsoredSection = .ads
    @State private var showCreateAd = false
    @State private var showCreateGarageSale = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                Picker("Section", selection: $selectedSection) {
                    ForEach(SponsoredSection.allCases) { section in
                        Text(section.rawValue).tag(section)
                    }
                }
                .pickerStyle(.segmented)
            }

            if selectedSection == .ads {
                sponsoredAdsSection
            } else {
                garageSalesSection
            }
        }
        .listStyle(.plain)
        .navigationTitle("Sponsored")
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 0) {
                Divider()
                Button {
                    if selectedSection == .ads {
                        showCreateAd = true
                    } else {
                        showCreateGarageSale = true
                    }
                } label: {
                    HStack {
                        Image(systemName: selectedSection == .ads ? "megaphone.fill" : "storefront.fill")
                        Text(selectedSection == .ads ? "Create Ad" : "Post Garage Sale")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .padding(.horizontal, 16)
                }
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, 12)
                .padding(.top, 10)
                .padding(.bottom, 8)
            }
        }
        .sheet(isPresented: $showCreateAd) {
            NavigationStack {
                CreateAdvertisementView(user: user, viewModel: viewModel) {
                    showCreateAd = false
                }
            }
        }
        .sheet(isPresented: $showCreateGarageSale) {
            NavigationStack {
                CreateGarageSaleView(user: user, viewModel: viewModel) {
                    showCreateGarageSale = false
                }
            }
        }
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
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
    private var sponsoredAdsSection: some View {
        Section("Sponsored Ads") {
            if viewModel.isLoading && viewModel.advertisements.isEmpty {
                ProgressView("Loading ads...")
            } else if viewModel.advertisements.isEmpty {
                Text("No ads available.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.advertisements) { ad in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(ad.title ?? "Ad")
                            .font(.headline)
                        Text(ad.description ?? "")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        if let mediaUrl = ad.mediaUrls?.first,
                           let url = URL(string: mediaUrl) {
                            AsyncImage(url: url) { phase in
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
                            .frame(height: 180)
                            .frame(maxWidth: .infinity)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }

                        if let sponsor = ad.sponsor, !sponsor.isEmpty {
                            Text("Sponsor: \(sponsor)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        HStack(spacing: 8) {
                            if let target = ad.targetUrl, let url = URL(string: target) {
                                Link("Open", destination: url)
                                    .font(.subheadline)
                            }
                            if let phone = ad.ownerPhone, let url = telURL(from: phone) {
                                Button("Call") { openURL(url) }
                                    .buttonStyle(.bordered)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                    .padding(10)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color(uiColor: .secondarySystemBackground))
                    )
                    .listRowInsets(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
                    .listRowSeparator(.hidden)
                }
            }
        }
    }

    @ViewBuilder
    private var garageSalesSection: some View {
        Section("Garage Sales") {
            if viewModel.isLoading && viewModel.garageSales.isEmpty {
                ProgressView("Loading garage sales...")
            } else if viewModel.garageSales.isEmpty {
                Text("No garage sales available.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.garageSales) { sale in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(sale.title ?? "Garage Sale")
                            .font(.headline)
                        Text(sale.description ?? "")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        if let mediaUrl = sale.media?.first?.url,
                           let url = URL(string: mediaUrl) {
                            AsyncImage(url: url) { phase in
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
                            .frame(height: 180)
                            .frame(maxWidth: .infinity)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }

                        Text("\(sale.city ?? "") \(sale.state ?? "")")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        HStack(spacing: 8) {
                            if let phone = sale.contactPhone, let url = telURL(from: phone) {
                                Button("Call") { openURL(url) }
                                    .buttonStyle(.bordered)
                            }
                            if let email = sale.contactEmail, let url = emailURL(from: email) {
                                Button("Email") { openURL(url) }
                                    .buttonStyle(.bordered)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                    .padding(10)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color(uiColor: .secondarySystemBackground))
                    )
                    .listRowInsets(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
                    .listRowSeparator(.hidden)
                }
            }
        }
    }

    private func telURL(from raw: String) -> URL? {
        let digits = raw.filter { "0123456789+".contains($0) }
        guard !digits.isEmpty else { return nil }
        return URL(string: "tel://\(digits)")
    }

    private func emailURL(from raw: String) -> URL? {
        let email = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !email.isEmpty else { return nil }
        return URL(string: "mailto:\(email)")
    }
}
