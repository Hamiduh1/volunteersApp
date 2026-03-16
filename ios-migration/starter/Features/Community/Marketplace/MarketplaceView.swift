import SwiftUI

struct MarketplaceView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = MarketplaceViewModel()

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Post Item") {
                TextField("Title", text: $viewModel.title)
                TextField("Description", text: $viewModel.description, axis: .vertical)
                    .lineLimit(2...4)
                TextField("Price (USD)", text: $viewModel.price)
                    .keyboardType(.decimalPad)
                Picker("Category", selection: $viewModel.category) {
                    ForEach(viewModel.categories.filter { $0 != "All" }, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
                Button {
                    Task { await viewModel.post(user: user) }
                } label: {
                    if viewModel.isPosting {
                        ProgressView()
                    } else {
                        Text("Post Marketplace Item")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canPost)
            }

            Section("Filter") {
                Picker("Category", selection: $viewModel.selectedCategory) {
                    ForEach(viewModel.categories, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
                .pickerStyle(.menu)
            }

            Section("Listings") {
                if viewModel.isLoading && viewModel.filteredItems.isEmpty {
                    ProgressView("Loading marketplace...")
                } else if viewModel.filteredItems.isEmpty {
                    Text("No listings yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.filteredItems) { item in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(item.title ?? "Untitled")
                                .font(.headline)
                            Text(item.description ?? "")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text(String(format: "$%.2f", item.price ?? 0))
                                .font(.subheadline.weight(.semibold))
                            Text("\(item.sellerName ?? "Seller") - \(item.category ?? "Other")")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let ts = item.timestamp?.dateValue() {
                                Text(ts.formatted(date: .abbreviated, time: .shortened))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Marketplace")
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
