import SwiftUI

struct OwnerUserReportsView: View {
    @StateObject private var viewModel = OwnerUserReportsViewModel()

    var body: some View {
        List {
            if viewModel.isLoading && viewModel.reports.isEmpty {
                ProgressView("Loading user reports...")
            } else if viewModel.reports.isEmpty {
                Text("No user reports found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.reports) { report in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(report.reportedUserName)
                            .font(.headline)
                        Text(report.reason)
                            .font(.subheadline)
                        Text("Source: \(report.sourceCollection)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if let reporter = report.reportingUserDisplayName {
                            Text("Reporter: \(reporter)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if let eventName = report.eventName {
                            Text("Context: \(eventName)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if let at = report.timestamp {
                            Text(at.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("User Reports")
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
