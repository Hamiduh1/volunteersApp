import SwiftUI

struct OrganizerEventComposerView: View {
    let user: AppSessionUser
    let existingEvent: EventRecord?
    let onSaved: () -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: OrganizerEventComposerViewModel

    init(
        user: AppSessionUser,
        existingEvent: EventRecord? = nil,
        onSaved: @escaping () -> Void
    ) {
        self.user = user
        self.existingEvent = existingEvent
        self.onSaved = onSaved
        _viewModel = StateObject(
            wrappedValue: OrganizerEventComposerViewModel(existingEvent: existingEvent)
        )
    }

    var body: some View {
        NavigationStack {
            Form {
                if let status = viewModel.statusMessage, !status.isEmpty {
                    Section {
                        Text(status)
                            .font(.subheadline)
                            .foregroundStyle(.green)
                    }
                }

                Section("Event Details") {
                    TextField("Title", text: $viewModel.title)
                    TextField("Description", text: $viewModel.description, axis: .vertical)
                        .lineLimit(3...6)
                    TextField("Category", text: $viewModel.category)
                    DatePicker("Date & Time", selection: $viewModel.eventDate, displayedComponents: [.date, .hourAndMinute])
                }

                Section("Location") {
                    TextField("Location Name", text: $viewModel.locationName)
                    TextField("Address", text: $viewModel.locationAddress)
                }

                Section("Capacity & Payment") {
                    TextField("Volunteer Limit", text: $viewModel.volunteerLimitText)
                        .keyboardType(.numberPad)
                    TextField("Payment per Volunteer (USD)", text: $viewModel.paymentText)
                        .keyboardType(.decimalPad)
                }

                Section {
                    Button {
                        Task {
                            let created = await viewModel.create(uid: user.uid)
                            if created {
                                onSaved()
                                dismiss()
                            }
                        }
                    } label: {
                        if viewModel.isSubmitting {
                            ProgressView()
                        } else {
                            Text(viewModel.isEditMode ? "Save Changes" : "Create Event")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.isSubmitting)
                }
            }
            .navigationTitle(viewModel.isEditMode ? "Edit Event" : "New Event")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
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
}
