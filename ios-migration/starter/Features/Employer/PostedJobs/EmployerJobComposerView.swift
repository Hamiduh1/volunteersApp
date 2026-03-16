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

                Section("Job Details") {
                    TextField("Title", text: $viewModel.title)
                    TextField("Description", text: $viewModel.description, axis: .vertical)
                        .lineLimit(3...6)
                    TextField("Location", text: $viewModel.locationString)
                    TextField("Category", text: $viewModel.category)
                    TextField("Job Type", text: $viewModel.jobType)
                    TextField("Salary / Compensation", text: $viewModel.salaryOrCompensation)
                    DatePicker("Application Deadline", selection: $viewModel.applicationDeadline, displayedComponents: [.date, .hourAndMinute])
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
            .navigationTitle(viewModel.isEditMode ? "Edit Job" : "New Job")
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
