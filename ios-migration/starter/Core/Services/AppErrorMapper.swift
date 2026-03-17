import Foundation
import FirebaseAuth

enum AppErrorMapper {
    static func message(from error: Error) -> String {
        let nsError = error as NSError
        if nsError.domain == AuthErrorDomain,
           let code = AuthErrorCode.Code(rawValue: nsError.code) {
            switch code {
            case .emailAlreadyInUse:
                return "This email is already registered. Sign in or reset the password."
            case .invalidEmail:
                return "Enter a valid email address."
            case .wrongPassword:
                return "Incorrect password."
            case .userNotFound:
                return "No account found for this email."
            case .weakPassword:
                return "Password is too weak. Use at least 6 characters."
            case .networkError:
                return "Network unavailable. Check your connection and try again."
            case .tooManyRequests:
                return "Too many attempts. Please wait a moment and try again."
            case .internalError:
                return "Authentication service is temporarily unavailable. Please try again."
            default:
                break
            }
        }

        let raw = nsError.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = raw.lowercased()

        if lower.contains("permission_denied") || lower.contains("insufficient permissions") {
            return "Permission denied for this action. Check Firestore/Storage rules and user role access."
        }
        if lower.contains("failed_precondition") && (lower.contains("index") || lower.contains("query requires")) {
            return "This query needs a Firestore index. Create/deploy the required index and try again."
        }
        if lower.contains("unauthenticated") || lower.contains("authentication") || lower.contains("auth") {
            return "Your session may be expired. Sign in again and retry."
        }
        if lower.contains("not_found") || lower.contains("not found") {
            return "Requested data was not found."
        }
        if lower.contains("deadline-exceeded") || lower.contains("timed out") || lower.contains("timeout") {
            return "Request timed out. Please retry."
        }
        if lower.contains("network") || lower.contains("offline") || lower.contains("unable to resolve host") {
            return "Network unavailable. Check your connection and try again."
        }
        if lower.contains("internal error") || lower.contains("internalerror") {
            return "Authentication service is temporarily unavailable. Please try again."
        }
        if lower.contains("app check") {
            return "Security validation failed. Reopen the app and try again."
        }

        if raw.isEmpty {
            return "Something went wrong. Please try again."
        }
        return raw
    }
}
