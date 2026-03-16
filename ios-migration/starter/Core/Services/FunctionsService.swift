import Foundation
import FirebaseFunctions

final class FunctionsService {
    static let shared = FunctionsService()
    private init() {}

    private let functions = Functions.functions()

    func call(function: CallableFunction, data: [String: Any]? = nil) async throws -> Any? {
        if let data {
            return try await functions.httpsCallable(function.rawValue).call(data).data
        }
        return try await functions.httpsCallable(function.rawValue).call().data
    }

    func callMap(function: CallableFunction, data: [String: Any]? = nil) async throws -> [String: Any] {
        let result = try await call(function: function, data: data)
        return result as? [String: Any] ?? [:]
    }
}
