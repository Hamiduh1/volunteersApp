import Foundation
import FirebaseAuth
import FirebaseFirestore

struct LoginRoleOption: Identifiable, Equatable {
    let id: String
    let label: String
    let value: String
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var email = ""
    @Published var password = ""
    @Published var selectedRole = "volunteer"
    @Published var showStaffRoles = false

    @Published var isLoading = false
    @Published var isResendingVerification = false
    @Published var resendCooldownSeconds = 0
    @Published var errorMessage: String?
    @Published var infoMessage: String?

    let primaryRoleOptions: [LoginRoleOption] = [
        LoginRoleOption(id: "volunteer", label: "Volunteer", value: "volunteer"),
        LoginRoleOption(id: "organizer", label: "Organizer", value: "organizer"),
        LoginRoleOption(id: "employer", label: "Employer", value: "employer")
    ]
    let staffRoleOptions: [LoginRoleOption] = [
        LoginRoleOption(id: "admin", label: "Admin", value: "admin"),
        LoginRoleOption(id: "associate", label: "Associate", value: "associate")
    ]

    var activeRoleOptions: [LoginRoleOption] {
        showStaffRoles ? staffRoleOptions : primaryRoleOptions
    }

    private let db = Firestore.firestore()
    private let verificationCooldownSeconds = 120
    private let rolePreferenceKey = "auth_preferences_last_selected_role"
    private lazy var staffRoleValues: Set<String> = Set(staffRoleOptions.map(\.value))
    private var resendCooldownTask: Task<Void, Never>?

    init() {
        if let saved = UserDefaults.standard.string(forKey: rolePreferenceKey),
           primaryRoleOptions.contains(where: { $0.value == saved }) || staffRoleOptions.contains(where: { $0.value == saved }) {
            selectedRole = saved
            showStaffRoles = staffRoleValues.contains(saved)
        }
    }

    deinit {
        resendCooldownTask?.cancel()
    }

    func roleLabel(for roleValue: String) -> String {
        (primaryRoleOptions + staffRoleOptions).first(where: { $0.value == roleValue })?.label ?? "Volunteer"
    }

    func setSelectedRole(_ roleValue: String) {
        selectedRole = roleValue
        UserDefaults.standard.set(roleValue, forKey: rolePreferenceKey)
    }

    func toggleRoleGroup() {
        showStaffRoles.toggle()
        if showStaffRoles {
            if !staffRoleValues.contains(selectedRole) {
                setSelectedRole(staffRoleOptions.first?.value ?? "admin")
            }
        } else if staffRoleValues.contains(selectedRole) {
            setSelectedRole(primaryRoleOptions.first?.value ?? "volunteer")
        }
    }

    func signIn() async {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanEmail.isEmpty else {
            errorMessage = "Email is required."
            return
        }
        guard !password.isEmpty else {
            errorMessage = "Password is required."
            return
        }

        isLoading = true
        errorMessage = nil
        infoMessage = nil
        defer { isLoading = false }

        do {
            let user = try await AuthService.shared.signIn(email: cleanEmail, password: password)
            try await user.reload()

            if !user.isEmailVerified {
                try? AuthService.shared.signOut()
                throw NSError(
                    domain: "LoginViewModel",
                    code: 1001,
                    userInfo: [
                        NSLocalizedDescriptionKey:
                            "Your email is not verified. Tap 'Resend verification email', then enter the latest 6-digit code."
                    ]
                )
            }

            var userDoc = try await db.collection("users").document(user.uid).getDocument()
            if !userDoc.exists {
                try? AuthService.shared.signOut()
                throw NSError(
                    domain: "LoginViewModel",
                    code: 1002,
                    userInfo: [NSLocalizedDescriptionKey: "User profile not found in database."]
                )
            }

            var firestoreUserType = try await resolveUserType(userId: user.uid, userDoc: userDoc)
            let normalizedSelectedRole = normalizeRoleForLogin(selectedRole)
            var normalizedFirestoreRole = normalizeRoleForLogin(firestoreUserType)

            if normalizedSelectedRole == "admin", normalizedFirestoreRole != normalizedSelectedRole {
                do {
                    let response = try await FunctionsService.shared.callMap(function: .bootstrapOwnerSelf)
                    if (response["success"] as? Bool) == true {
                        userDoc = try await db.collection("users").document(user.uid).getDocument()
                        firestoreUserType = try await resolveUserType(userId: user.uid, userDoc: userDoc)
                        normalizedFirestoreRole = normalizeRoleForLogin(firestoreUserType)
                    }
                } catch {
                    // If bootstrap fails, continue with normal role mismatch handling.
                }
                userDoc = try await db.collection("users").document(user.uid).getDocument()
            }

            if normalizedFirestoreRole != normalizedSelectedRole {
                try? AuthService.shared.signOut()
                throw NSError(
                    domain: "LoginViewModel",
                    code: 1003,
                    userInfo: [NSLocalizedDescriptionKey: "Role mismatch. You are registered as \(firestoreUserType)."]
                )
            }

            if normalizedFirestoreRole == "admin" || normalizedFirestoreRole == "associate" {
                let staffStatus = (userDoc.get("staffOnboardingStatus") as? String)?
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .uppercased() ?? "ACTIVE"
                if staffStatus != "ACTIVE" {
                    try? AuthService.shared.signOut()
                    throw NSError(
                        domain: "LoginViewModel",
                        code: 1004,
                        userInfo: [NSLocalizedDescriptionKey: "Your staff account is pending approval. Contact an admin to activate access."]
                    )
                }
            }

            if normalizedSelectedRole == "employer" {
                try await ensureEmployerProfile(for: user, userDoc: userDoc)
            }

            infoMessage = nil
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func resendVerificationEmail() async {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanEmail.isEmpty, !cleanPassword.isEmpty else {
            errorMessage = "Enter your email and password first, then tap resend."
            return
        }
        guard resendCooldownSeconds == 0 else {
            infoMessage = "Please wait \(resendCooldownSeconds)s before requesting another verification email."
            return
        }

        isResendingVerification = true
        errorMessage = nil
        infoMessage = nil
        defer { isResendingVerification = false }

        do {
            let user = try await AuthService.shared.signIn(email: cleanEmail, password: cleanPassword)
            try await user.reload()

            if user.isEmailVerified {
                try? AuthService.shared.signOut()
                infoMessage = "Your email is already verified. Log in now."
                return
            }

            let cooldown = try await AuthService.shared.requestEmailVerificationCode()
            try? AuthService.shared.signOut()
            startResendCooldown(cooldown)
            infoMessage = "Verification email sent. Enter the latest 6-digit code from your email."
        } catch {
            try? AuthService.shared.signOut()
            if let cooldown = extractCooldownSeconds(from: error), cooldown > 0 {
                startResendCooldown(cooldown)
            }
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func startResendCooldown(_ seconds: Int) {
        let safeSeconds = max(0, seconds)
        resendCooldownTask?.cancel()
        resendCooldownSeconds = safeSeconds
        guard safeSeconds > 0 else { return }

        resendCooldownTask = Task { [weak self] in
            guard let self else { return }
            var remaining = safeSeconds
            while remaining > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                remaining -= 1
                await MainActor.run {
                    self.resendCooldownSeconds = remaining
                }
            }
        }
    }

    private func resolveUserType(userId: String, userDoc: DocumentSnapshot) async throws -> String {
        let existing = [
            userDoc.get("userType") as? String,
            userDoc.get("role") as? String,
            userDoc.get("userRole") as? String
        ]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
            .first { !$0.isEmpty }
        if let existing {
            return existing
        }

        let employerDoc = try await db.collection("employers").document(userId).getDocument()
        let organizerDoc = try await db.collection("organizers").document(userId).getDocument()
        let employerExists = employerDoc.exists
        let organizerExists = organizerDoc.exists

        let inferredType: String
        if employerExists {
            inferredType = "employer"
        } else if organizerExists {
            inferredType = "organizer"
        } else {
            inferredType = "volunteer"
        }

        try? await db.collection("users")
            .document(userId)
            .setData(
                [
                    "userType": inferredType,
                    "role": inferredType
                ],
                merge: true
            )

        return inferredType
    }

    private func ensureEmployerProfile(for user: User, userDoc: DocumentSnapshot) async throws {
        let employerRef = db.collection("employers").document(user.uid)
        let employerDoc = try await employerRef.getDocument()
        if employerDoc.exists { return }

        let organizationName =
            (userDoc.get("organizationName") as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            ?? (userDoc.get("companyName") as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            ?? (userDoc.get("name") as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            ?? "Employer"

        try await employerRef.setData(
            [
                "organizationName": organizationName,
                "contactEmail": user.email ?? "",
                "createdAt": FieldValue.serverTimestamp()
            ],
            merge: true
        )
    }

    private func normalizeRoleForLogin(_ roleValue: String?) -> String? {
        guard let role = roleValue?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !role.isEmpty else {
            return nil
        }
        switch role {
        case "owner", "admin":
            return "admin"
        case "associate", "support", "support_associate":
            return "associate"
        default:
            return role
        }
    }

    private func extractCooldownSeconds(from error: Error) -> Int? {
        let nsError = error as NSError
        if let details = nsError.userInfo["details"] as? [String: Any],
           let cooldown = (details["cooldownSeconds"] as? NSNumber)?.intValue {
            return cooldown
        }
        if let cooldown = (nsError.userInfo["cooldownSeconds"] as? NSNumber)?.intValue {
            return cooldown
        }
        return nil
    }
}
