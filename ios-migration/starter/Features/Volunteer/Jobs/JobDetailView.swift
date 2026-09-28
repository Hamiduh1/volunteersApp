import SwiftUI

struct JobDetailView: View {
    let jobId: String
    let user: AppSessionUser
    @StateObject private var viewModel = JobDetailViewModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if viewModel.isLoading {
                    ProgressView("Loading job...")
                } else if let job = viewModel.job {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(job.title ?? "Job")
                            .font(.title2.weight(.bold))
                        if let role = job.jobTitle, !role.isEmpty {
                            Text(role)
                                .font(.headline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.bottom, 2)

                    infoCard(
                        title: "Employer",
                        rows: [
                            ("Organization", job.organizationName ?? job.employerName ?? "Unknown"),
                            ("Category", (job.category?.isEmpty == false ? job.category! : "General")),
                            ("Type", (job.jobType?.isEmpty == false ? job.jobType! : "Volunteer"))
                        ]
                    )

                    infoCard(
                        title: "Schedule",
                        rows: [
                            ("Date", (job.date?.isEmpty == false ? job.date! : "TBD")),
                            ("Time", (job.time?.isEmpty == false ? job.time! : "TBD")),
                            ("Location", job.locationName ?? job.locationString ?? "Unavailable")
                        ]
                    )

                    infoCard(
                        title: "Requirements",
                        rows: [
                            ("Volunteers Needed", "\(job.volunteersNeeded ?? job.totalSlots ?? 0)"),
                            ("Compensation", job.salaryOrCompensation?.isEmpty == false ? job.salaryOrCompensation! : "Volunteer")
                        ]
                    )

                    if let skills = job.requiredSkills, !skills.isEmpty {
                        Text("Required Skills")
                            .font(.headline)
                        Text(skills.joined(separator: ", "))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    if let preferred = job.preferredSkills, !preferred.isEmpty {
                        Text("Preferred Skills")
                            .font(.headline)
                        Text(preferred.joined(separator: ", "))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    if let responsibilities = job.responsibilities, !responsibilities.isEmpty {
                        Text("Responsibilities")
                            .font(.headline)
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(responsibilities, id: \.self) { item in
                                Text("* \(item)")
                                    .font(.subheadline)
                            }
                        }
                    }

                    Text("Description")
                        .font(.headline)
                    Text(job.description?.isEmpty == false ? job.description! : "No description provided.")
                        .font(.body)

                    if viewModel.actionState != .canApply {
                        Text("Application status: \((viewModel.application?.status ?? .unknown).displayTitle)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Text("Job not found.")
                        .foregroundStyle(.secondary)
                }
            }
            .padding()
        }
        .navigationTitle("Job Details")
        .safeAreaInset(edge: .bottom) {
            if !viewModel.isLoading, viewModel.job != nil {
                applyActionBar
            }
        }
        .task { await viewModel.load(jobId: jobId, user: user) }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    private var applyActionBar: some View {
        let state = viewModel.actionState
        let config: (title: String, tint: Color, enabled: Bool) = {
            switch state {
            case .canApply:
                return ("Apply Now", .blue, true)
            case .appliedPending:
                return ("Application Sent", .gray, false)
            case .approved:
                return ("Application Approved", .green, false)
            case .rejected:
                return ("Not Selected", .red, false)
            case .jobClosed:
                return ("Job Closed", .gray, false)
            case .checking:
                return ("Checking Status...", .gray, false)
            }
        }()

        return VStack(spacing: 6) {
            Button {
                Task { await viewModel.apply(jobId: jobId, user: user) }
            } label: {
                if viewModel.isApplying {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 24)
                } else {
                    Text(config.title)
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(config.tint)
            .disabled(!config.enabled || viewModel.isApplying)
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .background(.ultraThinMaterial)
    }

    private func infoCard(title: String, rows: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
            ForEach(rows.indices, id: \.self) { idx in
                HStack(alignment: .top) {
                    Text(rows[idx].0)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(width: 130, alignment: .leading)
                    Text(rows[idx].1)
                        .font(.subheadline)
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
