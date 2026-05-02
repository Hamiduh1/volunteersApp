import SwiftUI

struct SupportConsoleView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = SupportConsoleViewModel()

    private var isAdmin: Bool {
        user.role == .owner || user.role == .admin
    }

    var body: some View {
        NavigationStack {
            List {
                if let status = viewModel.statusMessage, !status.isEmpty {
                    Section {
                        Text(status)
                            .font(.subheadline)
                            .foregroundStyle(.green)
                    }
                }

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

                    TextField("Filter loaded users", text: $viewModel.userFilterQuery)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

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

                if isAdmin {
                    Section("Add Support Associate") {
                        TextField("Associate email", text: $viewModel.associateEmail)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        if !viewModel.associateEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                           let validation = viewModel.associateEmailValidationMessage,
                           !validation.isEmpty {
                            Text(validation)
                                .font(.caption)
                                .foregroundStyle(.orange)
                        }
                        Button("Grant Access") {
                            Task { await viewModel.addAssociate() }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(!viewModel.canAddAssociate)
                    }
                }

                Section("Users") {
                    if viewModel.isLoadingUsers && viewModel.filteredUsers.isEmpty {
                        ProgressView("Loading users...")
                    } else if viewModel.filteredUsers.isEmpty {
                        Text("No support users found.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.filteredUsers) { user in
                            Button {
                                viewModel.selectUser(user)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(user.username).font(.headline)
                                    Text(user.email).font(.caption).foregroundStyle(.secondary)
                                    Text("\(user.role) - \(user.walletCurrency) \(String(format: "%.2f", user.walletBalance))")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 2)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .listRowBackground(
                                viewModel.selectedUser?.id == user.id ? Color.blue.opacity(0.08) : Color.clear
                            )
                        }
                    }
                }

                if let selected = viewModel.selectedUser {
                    Section("Selected User") {
                        Text(selected.username).font(.headline)
                        Text(selected.email).font(.caption).foregroundStyle(.secondary)

                        if !isAdmin {
                            TextField("Verify email", text: $viewModel.verificationEmail)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                            TextField("Verify phone", text: $viewModel.verificationPhone)
                                .keyboardType(.phonePad)
                        }

                        HStack {
                            Button(isAdmin ? "Open Account Detail" : "Verify & Open Account") {
                                Task { await viewModel.loadDetails(canBypassVerification: isAdmin) }
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(viewModel.isLoadingDetails)

                            Button("Clear") {
                                viewModel.clearSelection()
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                if let details = viewModel.details {
                    Section("Account Details") {
                        detailRow("User ID", details.userId)
                        detailRow("Role", details.role)
                        detailRow("Email", details.email)
                        detailRow("Phone", details.phone)
                        detailRow("Wallet", "\(details.walletCurrency) \(String(format: "%.2f", details.walletBalance))")
                        detailRow("Payouts Enabled", details.payoutsEnabled ? "Yes" : "No")
                        detailRow("Charges Enabled", details.chargesEnabled ? "Yes" : "No")
                        detailRow("Details Submitted", details.detailsSubmitted ? "Yes" : "No")
                    }

                    Section("Complaints") {
                        TextField("Filter complaints", text: $viewModel.complaintsQuery)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()

                        if viewModel.filteredComplaints.isEmpty {
                            Text("No complaints found.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(viewModel.filteredComplaints.prefix(40)) { complaint in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(complaint.reason).font(.subheadline)
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
                                .padding(.vertical, 2)
                            }
                        }
                    }

                    Section("Recent Transactions") {
                        Picker("Status", selection: $viewModel.transactionFilter) {
                            ForEach(SupportTransactionFilter.allCases) { filter in
                                Text(filter.title).tag(filter)
                            }
                        }
                        .pickerStyle(.menu)
                        TextField("Filter transactions", text: $viewModel.transactionsQuery)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()

                        if viewModel.filteredTransactions.isEmpty {
                            Text("No transactions found.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(viewModel.filteredTransactions.prefix(60)) { tx in
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(tx.title).font(.subheadline)
                                        Text("\(tx.type) - \(tx.status)")
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
                                        .fontWeight(.semibold)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Support Console")
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

