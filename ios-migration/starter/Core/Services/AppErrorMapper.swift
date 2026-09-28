import Foundation
import FirebaseAuth

enum AppErrorMapper {
    static func message(from error: Error) -> String {
        let nsError = error as NSError
        if let diagnostic = firebaseAuthDiagnosticMessage(from: nsError) {
            return diagnostic
        }

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

    /// Pulls Firebase Auth server details from nested NSError.userInfo payloads.
    /// This converts opaque "internal error" results into actionable setup messages.
    private static func firebaseAuthDiagnosticMessage(from error: NSError) -> String? {
        let details = nestedFirebaseAuthResponse(from: error)
        let message = (details["message"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased() ?? ""
        let status = (details["status"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased() ?? ""

        if message.contains("requests to this api identitytoolkit method") && message.contains("are blocked") {
            return "Firebase Auth is blocked for this iOS app key. In Google Cloud Console, update that API key's restrictions to include Identity Toolkit/Authentication APIs, and confirm the iOS app uses the matching GoogleService-Info.plist."
        }

        if message.contains("api key not valid") || message.contains("invalid_api_key") {
            return "This iOS build is using an invalid Firebase API key. Replace GoogleService-Info.plist with the one for this Firebase project and rebuild."
        }

        if message.contains("app is not authorized") || message.contains("app not authorized") || message.contains("app_not_authorized") {
            return "This iOS bundle ID is not authorized for the Firebase API key. In Google Cloud Console credentials, verify the key's iOS application restriction matches this app's bundle ID."
        }

        if message.contains("configuration_not_found") {
            return "Firebase Auth configuration was not found for this app. Verify the iOS app is registered in Firebase and that GoogleService-Info.plist belongs to the same project."
        }

        if message.contains("firebase app check token is invalid") || status == "UNAUTHENTICATED" {
            return "Firebase App Check validation failed during sign-in. Verify App Check provider setup for iOS and try again."
        }

        return nil
    }

    private static func nestedFirebaseAuthResponse(from error: NSError) -> [String: Any] {
        if let direct = error.userInfo["FIRAuthErrorUserInfoDeserializedResponseKey"] as? [String: Any] {
            return direct
        }
        if let underlying = error.userInfo[NSUnderlyingErrorKey] as? NSError {
            return nestedFirebaseAuthResponse(from: underlying)
        }
        return [:]
    }
}
