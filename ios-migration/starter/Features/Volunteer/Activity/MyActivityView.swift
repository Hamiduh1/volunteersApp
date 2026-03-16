import SwiftUI

struct MyActivityView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = MyActivityViewModel()

    var body: some View {
        NavigationStack {
            VStack {
                Picker("Type", selection: $viewModel.selectedType) {
                    ForEach(VolunteerActivityType.allCases) { type in
                        Text(type.title).tag(type)
                    }
                }
                .pickerStyle(.segmented)
                .padding([.horizontal, .top])

                if viewModel.isLoading {
                    Spacer()
                    ProgressView("Loading activity...")
                    Spacer()
                } else if viewModel.filteredItems.isEmpty {
                    Spacer()
                    Text("No activity yet.")
                        .foregroundStyle(.secondary)
                    Spacer()
                } else {
                    List(viewModel.filteredItems) { item in
                        switch item.type {
                        case .event:
                            NavigationLink {
                                EventDetailView(eventId: item.referenceId, user: user)
                            } label: {
                                ActivityRow(item: item)
                            }
                        case .job:
                            NavigationLink {
                                JobDetailView(jobId: item.referenceId, user: user)
                            } label: {
                                ActivityRow(item: item)
                            }
                        case .all:
                            ActivityRow(item: item)
                        }
                    }
                }
            }
            .navigationTitle("My Activity")
            .task { await viewModel.refresh(for: user) }
            .refreshable { await viewModel.refresh(for: user) }
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
}

private struct ActivityRow: View {
    let item: VolunteerActivityItem

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(item.title).font(.headline)
            Text(item.subtitle).font(.subheadline).foregroundStyle(.secondary)
            HStack {
                Text(item.type == .event ? "Event" : "Job")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Text(item.status.rawValue.replacingOccurrences(of: "_", with: " "))
                    .font(.caption.weight(.semibold))
            }
        }
        .padding(.vertical, 4)
    }
}
