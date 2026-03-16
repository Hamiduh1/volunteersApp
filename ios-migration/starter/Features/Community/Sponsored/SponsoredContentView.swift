import SwiftUI

struct SponsoredContentView: View {
    @StateObject private var viewModel = SponsoredContentViewModel()
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

            Section("Sponsored Ads") {
                if viewModel.isLoading && viewModel.advertisements.isEmpty {
                    ProgressView("Loading ads...")
                } else if viewModel.advertisements.isEmpty {
                    Text("No ads available.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.advertisements) { ad in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(ad.title ?? "Ad")
                                .font(.headline)
                            Text(ad.description ?? "")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
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
                    }
                }
            }

            Section("Garage Sales") {
                if viewModel.isLoading && viewModel.garageSales.isEmpty {
                    ProgressView("Loading garage sales...")
                } else if viewModel.garageSales.isEmpty {
                    Text("No garage sales available.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.garageSales) { sale in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(sale.title ?? "Garage Sale")
                                .font(.headline)
                            Text(sale.description ?? "")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
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
                    }
                }
            }
        }
        .navigationTitle("Sponsored")
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
