import Foundation

@MainActor
final class SignUpViewModel: ObservableObject {
    @Published var name = ""
    @Published var email = ""
    @Published var selectedRole = "volunteer"
    @Published var country = "United States"
    @Published var phoneNumber = ""
    @Published var companyName = ""
    @Published var birthDate = Calendar.current.date(byAdding: .year, value: -18, to: Date()) ?? Date()
    @Published var password = ""
    @Published var confirmPassword = ""

    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    let roleOptions: [LoginRoleOption] = [
        LoginRoleOption(id: "volunteer", label: "Volunteer", value: "volunteer"),
        LoginRoleOption(id: "organizer", label: "Organizer", value: "organizer"),
        LoginRoleOption(id: "employer", label: "Employer", value: "employer")
    ]

    let countryOptions: [String] = [
        "United States",
        "Canada",
        "United Kingdom",
        "Germany",
        "France",
        "Spain",
        "Italy",
        "Australia",
        "India",
        "Mexico",
        "Brazil",
        "Japan",
        "China",
        "Nigeria",
        "South Africa"
    ]

    var requiresCompanyName: Bool {
        selectedRole == "organizer" || selectedRole == "employer"
    }

    var requiresAgeVerification: Bool {
        selectedRole == "volunteer"
    }

    func signUp() async {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanPhone = phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCompany = companyName.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanName.isEmpty else {
            errorMessage = "Full name is required."
            return
        }
        guard !cleanEmail.isEmpty else {
            errorMessage = "Email is required."
            return
        }
        guard cleanEmail.contains("@") else {
            errorMessage = "Enter a valid email address."
            return
        }
        guard !cleanPhone.isEmpty else {
            errorMessage = "Phone number is required."
            return
        }
        guard password.count >= 6 else {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        guard password == confirmPassword else {
            errorMessage = "Passwords do not match."
            return
        }
        guard roleOptions.contains(where: { $0.value == selectedRole }) else {
            errorMessage = "Unsupported account type selected."
            return
        }
        if requiresCompanyName && cleanCompany.isEmpty {
            errorMessage = selectedRole == "organizer"
                ? "Organization name is required."
                : "Company name is required."
            return
        }
        if requiresAgeVerification {
            let cutoffDate = Calendar.current.date(byAdding: .year, value: -18, to: Date()) ?? Date()
            if birthDate > cutoffDate {
                errorMessage = "You must be 18 years or older to register as a volunteer."
                return
            }
        }

        let role = AppUserRole(rawRole: selectedRole)
        guard role != .unknown else {
            errorMessage = "Invalid account role."
            return
        }

        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let username = cleanName
                .lowercased()
                .replacingOccurrences(of: " ", with: "_")
            _ = try await AuthService.shared.signUp(
                email: cleanEmail,
                password: password,
                name: cleanName,
                username: username,
                phoneNumber: cleanPhone,
                country: country,
                companyName: requiresCompanyName ? cleanCompany : nil,
                birthDate: requiresAgeVerification ? birthDate : nil,
                role: role
            )

            try? AuthService.shared.signOut()
            statusMessage = "Account created. Enter the latest verification code from your email, then log in."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
