import SwiftUI

struct EmployerJobComposerView: View {
    let user: AppSessionUser
    let existingJob: JobRecord?
    let onSaved: () -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: EmployerJobComposerViewModel

    init(
        user: AppSessionUser,
        existingJob: JobRecord? = nil,
        onSaved: @escaping () -> Void
    ) {
        self.user = user
        self.existingJob = existingJob
        self.onSaved = onSaved
        _viewModel = StateObject(
            wrappedValue: EmployerJobComposerViewModel(existingJob: existingJob)
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

                Section("Opportunity Details") {
                    TextField("Organization Name", text: $viewModel.organizationName)
                    TextField("Opportunity Title", text: $viewModel.opportunityTitle)
                    TextField("Specific Role/Job Title", text: $viewModel.roleTitle)
                }

                Section("Schedule") {
                    DatePicker("Date & Time", selection: $viewModel.scheduledDate, displayedComponents: [.date, .hourAndMinute])
                }

                Section("Location & Category") {
                    TextField("Location", text: $viewModel.location)
                    Picker("Category", selection: $viewModel.category) {
                        ForEach(viewModel.categories, id: \.self) { category in
                            Text(category).tag(category)
                        }
                    }
                    .pickerStyle(.menu)

                    TextField("Volunteers Needed", text: $viewModel.volunteersNeeded)
                        .keyboardType(.numberPad)
                }

                Section("Description") {
                    TextField("Describe responsibilities and requirements", text: $viewModel.description, axis: .vertical)
                        .lineLimit(4...8)
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
                            Text(viewModel.isEditMode ? "Save Changes" : "Post Job")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.isSubmitting)
                }
            }
            .navigationTitle(viewModel.isEditMode ? "Edit Job/Opportunity" : "Post New Job/Opportunity")
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
