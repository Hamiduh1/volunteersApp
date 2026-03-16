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
                }

                if isAdmin {
                    Section("Add Support Associate") {
                        TextField("Associate email", text: $viewModel.associateEmail)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Button("Grant Access") {
                            Task { await viewModel.addAssociate() }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isAddingAssociate)
                    }
                }

                Section("Users") {
                    if viewModel.isLoadingUsers && viewModel.users.isEmpty {
                        ProgressView("Loading users...")
                    } else if viewModel.users.isEmpty {
                        Text("No support users found.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.users) { user in
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
                            }
                            .buttonStyle(.plain)
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

                        Button(isAdmin ? "Open Account Detail" : "Verify & Open Account") {
                            Task { await viewModel.loadDetails(canBypassVerification: isAdmin) }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isLoadingDetails)
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
                        if details.complaints.isEmpty {
                            Text("No complaints found.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(details.complaints.prefix(20)) { complaint in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(complaint.reason).font(.subheadline)
                                    Text("Reporter: \(complaint.reporterDisplayName ?? "Unknown")")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
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
                        if details.transactions.isEmpty {
                            Text("No transactions found.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(details.transactions.prefix(30)) { tx in
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(tx.title).font(.subheadline)
                                        Text("\(tx.type) - \(tx.status)")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
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

