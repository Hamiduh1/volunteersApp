import SwiftUI

struct EventDetailView: View {
    let eventId: String
    let user: AppSessionUser
    @StateObject private var viewModel = EventDetailViewModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if viewModel.isLoading {
                    ProgressView("Loading event...")
                } else if let event = viewModel.event {
                    Text(event.title ?? "Event")
                        .font(.title2).bold()

                    if let description = event.description, !description.isEmpty {
                        Text(description)
                    }

                    Text("Location: \(event.locationName ?? event.locationAddress ?? "TBD")")
                        .foregroundStyle(.secondary)

                    if let payment = event.payment {
                        Text(String(format: "Payment: $%.2f", payment))
                            .foregroundStyle(.secondary)
                    }

                    if viewModel.isApplied {
                        Text("Application status: \((viewModel.application?.status ?? .unknown).displayTitle)")
                            .font(.footnote)
                            .foregroundStyle(.green)
                    } else {
                        Button("Apply to Event") {
                            Task { await viewModel.apply(eventId: eventId, user: user) }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isApplying)
                        if viewModel.isApplying {
                            ProgressView()
                                .controlSize(.small)
                        }
                    }
                } else {
                    Text("Event not found.")
                        .foregroundStyle(.secondary)
                }
            }
            .padding()
        }
        .navigationTitle("Event Details")
        .task { await viewModel.load(eventId: eventId, user: user) }
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
