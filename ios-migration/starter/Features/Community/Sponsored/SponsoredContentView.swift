import SwiftUI

struct SponsoredContentView: View {
    @StateObject private var viewModel = SponsoredContentViewModel()

    var body: some View {
        List {
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
}
