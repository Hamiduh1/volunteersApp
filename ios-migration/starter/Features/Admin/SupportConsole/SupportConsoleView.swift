import SwiftUI

struct SupportConsoleView: View {
    let user: AppSessionUser
    let embedded: Bool
    @StateObject private var viewModel = SupportConsoleViewModel()

    init(user: AppSessionUser, embedded: Bool = false) {
        self.user = user
        self.embedded = embedded
        _viewModel = StateObject(wrappedValue: SupportConsoleViewModel())
    }

    private var isAdmin: Bool {
        user.role == .owner || user.role == .admin
    }

    var body: some View {
        Group {
            if embedded {
                consoleContent
            } else {
                NavigationStack {
                    consoleContent
                }
            }
        }
    }

    @ViewBuilder
    private var consoleContent: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard

                if let status = viewModel.statusMessage, !status.isEmpty {
                    statusBanner(status)
                }

<<<<<<< HEAD
                searchCard
=======
                AdminRoleResponsibilitiesSection(
                    screenKey: isAdmin
                    ? AdminRoleResponsibilitiesScreenKey.SUPPORT_CONSOLE_ADMIN
                    : AdminRoleResponsibilitiesScreenKey.SUPPORT_CONSOLE_ASSOCIATE,
                    fallbackGuide: isAdmin
                    ? AdminRoleResponsibilitiesGuide(
                        roleTitle: "Admin Support Lead",
                        mission: "Oversee support quality and protect customer accounts.",
                        responsibilities: [
                            "Grant and audit associate access.",
                            "Resolve complex account issues using strict verification standards.",
                            "Review complaint patterns and coordinate escalations with owner.",
                            "Document decisions on sensitive support cases."
                        ],
                        escalationRule: "Escalate suspected fraud or account takeover risk immediately."
                    )
                    : AdminRoleResponsibilitiesGuide(
                        roleTitle: "Support Associate",
                        mission: "Help users safely while following verification and policy rules.",
                        responsibilities: [
                            "Verify customer email and phone before opening account details.",
                            "Record accurate notes for each support action.",
                            "Escalate payout, security, or legal concerns to admin quickly.",
                            "Do not change fee/config/reversal controls."
                        ],
                        escalationRule: "Escalate any identity mismatch or payment dispute before action."
                    )
                )

                Section("Search Users") {
                    HStack {
                        TextField("Name, email, phone", text: $viewModel.query)
                            .textFieldStyle(.roundedBorder)
                        Button("Search") {
                            Task { await viewModel.loadUsers() }
                        }
                        .buttonStyle(.bordered)
                        .disabled(viewModel.isLoadingUsers)
                    }
>>>>>>> cc9d9a7 (iOS admin dashboard updates: country analytics, role responsibilities, fee config parity)

                if isAdmin {
                    addAssociateCard
                }

                usersCard

                if viewModel.selectedUser != nil {
                    selectedUserCard
                }

                if viewModel.details != nil {
                    accountDetailsCard
                    complaintsCard
                    transactionsCard
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .background(
            LinearGradient(
                colors: [
                    Color(.systemGroupedBackground),
                    Color(.secondarySystemGroupedBackground).opacity(0.38)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
        .navigationTitle("Support Console")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.loadUsers() }
        .refreshable { await viewModel.loadUsers() }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    @ViewBuilder
    private var heroCard: some View {
        supportCard(tint: .indigo) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "lifepreserver.fill")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.indigo)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Color.indigo.opacity(0.14)))

                VStack(alignment: .leading, spacing: 4) {
                    Text("Support Console")
                        .font(.headline.weight(.semibold))
                    Text("Search users, verify access, review complaints, and inspect recent wallet activity.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private func statusBanner(_ message: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.seal.fill")
                .foregroundStyle(.green)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.green.opacity(0.10))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.green.opacity(0.20), lineWidth: 1)
        )
    }

    @ViewBuilder
    private var searchCard: some View {
        supportCard(tint: .blue) {
            Text("Search Users")
                .font(.headline.weight(.semibold))

            HStack(spacing: 10) {
                TextField("Name, email, phone", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                Button {
                    Task { await viewModel.loadUsers() }
                } label: {
                    if viewModel.isLoadingUsers {
                        ProgressView()
                            .frame(width: 26, height: 26)
                    } else {
                        Image(systemName: "magnifyingglass")
                            .font(.headline.weight(.semibold))
                            .frame(width: 26, height: 26)
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(.blue)
                .disabled(viewModel.isLoadingUsers)
                .accessibilityLabel("Search users")
            }

            TextField("Filter loaded users", text: $viewModel.userFilterQuery)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.tertiarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            Picker("Role", selection: $viewModel.userRoleFilter) {
                ForEach(SupportUserRoleFilter.allCases) { filter in
                    Text(filter.title).tag(filter)
                }
            }
            .pickerStyle(.menu)

            Text("Showing \(viewModel.filteredUsers.count) of \(viewModel.users.count) users")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var addAssociateCard: some View {
        supportCard(tint: .purple) {
            Text("Add Support Associate")
                .font(.headline.weight(.semibold))

            TextField("Associate email", text: $viewModel.associateEmail)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.emailAddress)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.tertiarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            if !viewModel.associateEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
               let validation = viewModel.associateEmailValidationMessage,
               !validation.isEmpty {
                Text(validation)
                    .font(.caption)
                    .foregroundStyle(.orange)
            }

            Button {
                Task { await viewModel.addAssociate() }
            } label: {
                Label("Grant Access", systemImage: "person.badge.plus")
                    .font(.subheadline.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.purple)
            .disabled(!viewModel.canAddAssociate)
        }
    }

    @ViewBuilder
    private var usersCard: some View {
        supportCard(tint: .blue) {
            HStack {
                Text("Users")
                    .font(.headline.weight(.semibold))
                Spacer()
                if viewModel.isLoadingUsers {
                    ProgressView()
                        .controlSize(.small)
                }
            }

            if viewModel.isLoadingUsers && viewModel.filteredUsers.isEmpty {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Loading users...")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 6)
            } else if viewModel.filteredUsers.isEmpty {
                Text("No support users found.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                LazyVStack(spacing: 10) {
                    ForEach(viewModel.filteredUsers) { item in
                        let selected = viewModel.selectedUser?.id == item.id
                        Button {
                            viewModel.selectUser(item)
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.username)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)

                                Text(item.email)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)

                                Text("\(item.role) • \(item.walletCurrency) \(String(format: "%.2f", item.walletBalance))")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(selected ? Color.blue.opacity(0.10) : Color(.secondarySystemBackground))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(selected ? Color.blue.opacity(0.22) : Color.blue.opacity(0.12), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var selectedUserCard: some View {
        if let selected = viewModel.selectedUser {
            supportCard(tint: .teal) {
                Text("Selected User")
                    .font(.headline.weight(.semibold))

                Text(selected.username)
                    .font(.subheadline.weight(.semibold))
                Text(selected.email)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if !isAdmin {
                    TextField("Verify email", text: $viewModel.verificationEmail)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.emailAddress)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(Color(.tertiarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                    TextField("Verify phone", text: $viewModel.verificationPhone)
                        .keyboardType(.phonePad)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(Color(.tertiarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                HStack(spacing: 10) {
                    Button {
                        Task { await viewModel.loadDetails(canBypassVerification: isAdmin) }
                    } label: {
                        if viewModel.isLoadingDetails {
                            ProgressView()
                                .tint(.white)
                                .frame(maxWidth: .infinity)
                        } else {
                            Text(isAdmin ? "Open Account Detail" : "Verify & Open Account")
                                .font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.teal)
                    .disabled(viewModel.isLoadingDetails)

                    Button {
                        viewModel.clearSelection()
                    } label: {
                        Text("Clear")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }

    @ViewBuilder
    private var accountDetailsCard: some View {
        if let details = viewModel.details {
            supportCard(tint: .indigo) {
                Text("Account Details")
                    .font(.headline.weight(.semibold))

                detailRow("User ID", details.userId)
                detailRow("Role", details.role)
                detailRow("Email", details.email)
                detailRow("Phone", details.phone)
                detailRow("Wallet", "\(details.walletCurrency) \(String(format: "%.2f", details.walletBalance))")
                detailRow("Payouts Enabled", details.payoutsEnabled ? "Yes" : "No")
                detailRow("Charges Enabled", details.chargesEnabled ? "Yes" : "No")
                detailRow("Details Submitted", details.detailsSubmitted ? "Yes" : "No")
            }
        }
    }

    @ViewBuilder
    private var complaintsCard: some View {
        if viewModel.details != nil {
            supportCard(tint: .orange) {
                HStack {
                    Text("Complaints")
                        .font(.headline.weight(.semibold))
                    Spacer()
                    Text("\(min(viewModel.filteredComplaints.count, 40)) shown")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }

                TextField("Filter complaints", text: $viewModel.complaintsQuery)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                if viewModel.filteredComplaints.isEmpty {
                    Text("No complaints found.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    LazyVStack(spacing: 10) {
                        ForEach(viewModel.filteredComplaints.prefix(40)) { complaint in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(complaint.reason)
                                    .font(.subheadline.weight(.semibold))
                                Text("Reporter: \(complaint.reporterDisplayName ?? "Unknown")")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                if let eventName = complaint.eventName, !eventName.isEmpty {
                                    Text("Event: \(eventName)")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                if let at = complaint.timestamp {
                                    Text(at.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(Color.orange.opacity(0.06))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(Color.orange.opacity(0.14), lineWidth: 1)
                            )
                        }
                    }
                }
            }
        } else {
            EmptyView()
        }
    }

    @ViewBuilder
    private var transactionsCard: some View {
        if viewModel.details != nil {
            supportCard(tint: .green) {
                Text("Recent Transactions")
                    .font(.headline.weight(.semibold))

                Picker("Status", selection: $viewModel.transactionFilter) {
                    ForEach(SupportTransactionFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.menu)

                TextField("Filter transactions", text: $viewModel.transactionsQuery)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                if viewModel.filteredTransactions.isEmpty {
                    Text("No transactions found.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    LazyVStack(spacing: 10) {
                        ForEach(viewModel.filteredTransactions.prefix(60)) { tx in
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(tx.title)
                                        .font(.subheadline.weight(.semibold))
                                    Text("\(tx.type) • \(tx.status)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    if let time = tx.timestamp {
                                        Text(time.formatted(date: .abbreviated, time: .shortened))
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Text(String(format: "%.2f", tx.amount))
                                    .font(.subheadline.weight(.semibold))
                            }
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(Color.green.opacity(0.06))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(Color.green.opacity(0.14), lineWidth: 1)
                            )
                        }
                    }
                }
            }
        } else {
            EmptyView()
        }
    }

    @ViewBuilder
    private func supportCard<Content: View>(tint: Color, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.systemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(tint.opacity(0.14), lineWidth: 1)
        )
    }

    @ViewBuilder
    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value)
                .multilineTextAlignment(.trailing)
                .foregroundStyle(.secondary)
        }
    }
}
