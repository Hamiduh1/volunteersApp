import Foundation
import Combine

enum SupportUserRoleFilter: String, CaseIterable, Identifiable {
    case all
    case volunteer
    case organizer
    case employer
    case admin
    case owner
    case support
    case supportAssociate = "support_associate"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: return "All"
        case .supportAssociate: return "Support Associate"
        default: return rawValue.capitalized
        }
    }
}

enum SupportTransactionFilter: String, CaseIterable, Identifiable {
    case all
    case pending
    case completed
    case failed
    case refunded

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
}

@MainActor
final class SupportConsoleViewModel: ObservableObject {
    @Published private(set) var users: [SupportUserSummaryRecord] = []
    @Published var selectedUser: SupportUserSummaryRecord?
    @Published private(set) var details: SupportAccountDetailsRecord?

    @Published var query = "" // remote search query
    @Published var userFilterQuery = "" // local filter query
    @Published var userRoleFilter: SupportUserRoleFilter = .all
    @Published var verificationEmail = ""
    @Published var verificationPhone = ""
    @Published var associateEmail = ""
    @Published var complaintsQuery = ""
    @Published var transactionsQuery = ""
    @Published var transactionFilter: SupportTransactionFilter = .all

    @Published var isLoadingUsers = false
    @Published var isLoadingDetails = false
    @Published var isAddingAssociate = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    var filteredUsers: [SupportUserSummaryRecord] {
        let roleFiltered: [SupportUserSummaryRecord]
        switch userRoleFilter {
        case .all:
            roleFiltered = users
        default:
            roleFiltered = users.filter { $0.role.lowercased() == userRoleFilter.rawValue }
        }

        let cleanQuery = userFilterQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return roleFiltered }
        return roleFiltered.filter { user in
            user.username.lowercased().contains(cleanQuery)
                || user.email.lowercased().contains(cleanQuery)
                || user.phone.lowercased().contains(cleanQuery)
                || user.id.lowercased().contains(cleanQuery)
        }
    }

    var filteredComplaints: [SupportComplaintRecord] {
        guard let details else { return [] }
        let cleanQuery = complaintsQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return details.complaints }
        return details.complaints.filter { complaint in
            complaint.reason.lowercased().contains(cleanQuery)
                || (complaint.eventName?.lowercased().contains(cleanQuery) ?? false)
                || (complaint.reportedEmail?.lowercased().contains(cleanQuery) ?? false)
                || (complaint.reporterDisplayName?.lowercased().contains(cleanQuery) ?? false)
        }
    }

    var filteredTransactions: [SupportAccountTransactionRecord] {
        guard let details else { return [] }
        let byStatus: [SupportAccountTransactionRecord]
        switch transactionFilter {
        case .all:
            byStatus = details.transactions
        default:
            byStatus = details.transactions.filter { $0.status.lowercased() == transactionFilter.rawValue }
        }

        let cleanQuery = transactionsQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return byStatus }
        return byStatus.filter { tx in
            tx.title.lowercased().contains(cleanQuery)
                || tx.type.lowercased().contains(cleanQuery)
                || tx.status.lowercased().contains(cleanQuery)
                || (tx.source?.lowercased().contains(cleanQuery) ?? false)
                || (tx.note?.lowercased().contains(cleanQuery) ?? false)
        }
    }

    var canAddAssociate: Bool {
        associateEmailValidationMessage == nil && !isAddingAssociate
    }

    var associateEmailValidationMessage: String? {
        let email = associateEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !email.isEmpty else { return "Associate email is required." }
        guard isValidEmail(email) else { return "Enter a valid email address." }
        return nil
    }

    func loadUsers() async {
        isLoadingUsers = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoadingUsers = false }

        do {
            users = try await repository.listSupportUsers(query: query)
            if let selected = selectedUser {
                selectedUser = users.first(where: { $0.id == selected.id })
            }
            statusMessage = users.isEmpty ? "No matching users found." : "Loaded \(users.count) user(s)."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func selectUser(_ user: SupportUserSummaryRecord) {
        selectedUser = user
        details = nil
        verificationEmail = ""
        verificationPhone = ""
        complaintsQuery = ""
        transactionsQuery = ""
        transactionFilter = .all
        statusMessage = nil
        errorMessage = nil
    }

    func clearSelection() {
        selectedUser = nil
        details = nil
        verificationEmail = ""
        verificationPhone = ""
        complaintsQuery = ""
        transactionsQuery = ""
        transactionFilter = .all
        statusMessage = nil
        errorMessage = nil
    }

    func loadDetails(canBypassVerification: Bool) async {
        guard let selectedUser else {
            errorMessage = "Select a user first."
            return
        }

        let cleanEmail = verificationEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPhone = verificationPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        if !canBypassVerification, cleanEmail.isEmpty, cleanPhone.isEmpty {
            errorMessage = "Provide verification email or phone."
            return
        }

        isLoadingDetails = true
        errorMessage = nil
        defer { isLoadingDetails = false }

        do {
            let detailsResult = try await repository.getSupportAccountDetails(
                userId: selectedUser.id,
                verificationEmail: canBypassVerification ? nil : cleanEmail,
                verificationPhone: canBypassVerification ? nil : cleanPhone
            )
            details = detailsResult
            complaintsQuery = ""
            transactionsQuery = ""
            transactionFilter = .all
            statusMessage = canBypassVerification ? "Account details loaded." : "Verification passed. Account details loaded."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func addAssociate() async {
        let email = associateEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        if let validationMessage = associateEmailValidationMessage {
            errorMessage = validationMessage
            return
        }

        await performAddAssociate(email: email)
    }

    private func performAddAssociate(email: String) async {
        guard !email.isEmpty else {
            errorMessage = "Associate email is required."
            return
        }

        isAddingAssociate = true
        errorMessage = nil
        statusMessage = nil
        defer { isAddingAssociate = false }

        do {
            try await repository.addSupportAssociate(email: email)
            associateEmail = ""
            statusMessage = "Associate access granted for \(email)."
            await loadUsers()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func isValidEmail(_ value: String) -> Bool {
        let pattern = "^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        return value.range(of: pattern, options: .regularExpression) != nil
    }
}

