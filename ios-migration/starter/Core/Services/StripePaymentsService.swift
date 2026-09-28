import Foundation

#if canImport(StripePaymentSheet)
import StripePaymentSheet
#elseif canImport(StripePaymentsUI)
import StripePaymentsUI
#elseif canImport(StripeCore)
import StripeCore
#endif

enum StripePaymentsServiceError: LocalizedError {
    case missingPublishableKey
    case invalidPublishableKey

    var errorDescription: String? {
        switch self {
        case .missingPublishableKey:
            return "Stripe publishable key is missing from Info.plist."
        case .invalidPublishableKey:
            return "Stripe publishable key must start with pk_."
        }
    }
}

final class StripePaymentsService {
    static let shared = StripePaymentsService()

    private let lock = NSLock()
    private var configuredKey: String?

    private init() {}

    func configureIfPossible() {
        guard let publishableKey = resolvedPublishableKey() else { return }
        try? configure(with: publishableKey)
    }

    @discardableResult
    func configureFromBundle() throws -> String {
        guard let publishableKey = resolvedPublishableKey() else {
            throw StripePaymentsServiceError.missingPublishableKey
        }
        try configure(with: publishableKey)
    }

    @discardableResult
    func configure(with publishableKey: String) throws -> String {
        let cleanedKey = publishableKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedKey.isEmpty else {
            throw StripePaymentsServiceError.missingPublishableKey
        }
        guard cleanedKey.hasPrefix("pk_") else {
            throw StripePaymentsServiceError.invalidPublishableKey
        }

        lock.lock()
        defer { lock.unlock() }

        if configuredKey == cleanedKey {
            return cleanedKey
        }

        configuredKey = cleanedKey
        applyPublishableKey(cleanedKey)
        return cleanedKey
    }

    func resolvedPublishableKey() -> String? {
        let info = Bundle.main.infoDictionary ?? [:]
        let candidateKeys = ["STRIPE_PUBLISHABLE_KEY", "stripe_publishable_key"]
        for key in candidateKeys {
            if let value = info[key] as? String {
                let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cleaned.isEmpty {
                    return cleaned
                }
            }
        }
        return nil
    }

    private func applyPublishableKey(_ key: String) {
        #if canImport(StripePaymentSheet)
        STPAPIClient.shared.publishableKey = key
        #elseif canImport(StripePaymentsUI)
        STPAPIClient.shared.publishableKey = key
        #elseif canImport(StripeCore)
        StripeAPI.defaultPublishableKey = key
        #endif
    }
}
