import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

final class OwnerAdminRepository {
    private let db = Firestore.firestore()

    func fetchRevenueSummary() async throws -> OwnerRevenueSummaryRecord {
        let snapshot = try await db.collection(FirestoreCollection.system.rawValue)
            .document("platform_revenue")
            .getDocument()
        let data = snapshot.data() ?? [:]

        return OwnerRevenueSummaryRecord(
            totalCollected: data.double("totalCollected"),
            balance: data.double("balance"),
            stripeForexEarnings: data.double("stripeForexEarnings"),
            mobileMoneyHiddenFee: data.double("mobileMoneyHiddenFee"),
            blindDateFees: data.double("blindDateFees"),
            agentAuthorizationFees: data.double("agentAuthorizationFees"),
            agentCashoutOwnerShare: data.double("agentCashoutOwnerShare"),
            otherIncome: data.double("otherIncome"),
            transactionCount: Int(data.number("transactionCount")),
            lastUpdate: (data["lastUpdate"] as? Timestamp)?.dateValue()
        )
    }

    func fetchRevenueTransactions(limit: Int = 25) async throws -> [OwnerRevenueTransactionRecord] {
        let snapshot = try await db.collection(FirestoreCollection.system.rawValue)
            .document("platform_revenue")
            .collection(FirestoreSubcollection.transactions.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.map { doc in
            let data = doc.data()
            let createdAt = (data["createdAt"] as? Timestamp)?.dateValue()
                ?? (data["timestamp"] as? Timestamp)?.dateValue()
                ?? data.dateFromMillis("createdAtMs")
                ?? data.dateFromMillis("timestampMs")
            return OwnerRevenueTransactionRecord(
                id: doc.documentID,
                source: (data["source"] as? String) ?? "unknown",
                amount: data.double("amount"),
                note: data["note"] as? String,
                createdAt: createdAt
            )
        }
        .sorted {
            let l = $0.createdAt ?? .distantPast
            let r = $1.createdAt ?? .distantPast
            return l > r
        }
    }

    func cashOutOwnerRevenue() async throws -> String {
        let map = try await FunctionsService.shared.callMap(function: .cashOutOwnerRevenue)
        let data = (map["data"] as? [String: Any]) ?? map
        return (data["message"] as? String) ?? "Revenue moved to wallet."
    }

    func listPayoutRequests(
        statuses: [String]?,
        limit: Int = 180,
        mobileMoneyOnly: Bool = true
    ) async throws -> [AdminPayoutRequestRecord] {
        var payload: [String: Any] = [
            "limit": limit,
            "mobileMoneyOnly": mobileMoneyOnly
        ]
        if let statuses, !statuses.isEmpty {
            payload["statuses"] = statuses
        }

        let map = try await FunctionsService.shared.callMap(function: .adminListPayoutRequests, data: payload)
        let data = (map["data"] as? [String: Any]) ?? map
        let rawItems = (data["items"] as? [Any]) ?? []

        return rawItems.compactMap { raw in
            guard let row = coerceStringMap(raw) else { return nil }
            guard let payoutId = row.string("payoutRequestId") else { return nil }
            let createdAt = row.dateFromEpochGuess("createdAtMs")
                ?? row.dateFromEpochGuess("timestampMs")
                ?? row.dateFromEpochGuess("createdAt")
                ?? row.dateFromEpochGuess("timestamp")

            return AdminPayoutRequestRecord(
                id: payoutId,
                requesterId: row.string("senderId") ?? "",
                requesterName: row.string("recipientName") ?? row.string("senderName") ?? "User",
                amount: row.double("amount"),
                currency: (row.string("currency") ?? "USD").uppercased(),
                status: row.string("status") ?? "UNKNOWN",
                destinationLabel: row.string("recipientPhone") ?? row.string("recipientNetwork"),
                createdAt: createdAt
            )
        }
        .sorted {
            let l = $0.createdAt ?? .distantPast
            let r = $1.createdAt ?? .distantPast
            return l > r
        }
    }

    @discardableResult
    func reversePayoutRequests(ids: [String], reason: String) async throws -> [String: Any] {
        try await FunctionsService.shared.callMap(
            function: .adminReversePayoutRequestsCallable,
            data: [
                "payoutRequestIds": ids,
                "allowCompleted": false,
                "reason": reason
            ]
        )
    }

    func listSupportUsers(query: String?, limit: Int = 120) async throws -> [SupportUserSummaryRecord] {
        var payload: [String: Any] = ["limit": limit]
        if let query, !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            payload["query"] = query
        }

        let map = try await FunctionsService.shared.callMap(function: .supportListUsers, data: payload)
        let data = (map["data"] as? [String: Any]) ?? map
        let rawItems = (data["items"] as? [Any]) ?? []

        return rawItems.compactMap { raw in
            guard let row = coerceStringMap(raw) else { return nil }
            guard let userId = row.string("userId") else { return nil }
            return SupportUserSummaryRecord(
                id: userId,
                username: row.string("username") ?? "Unknown",
                email: row.string("email") ?? "",
                phone: row.string("phone") ?? "",
                role: row.string("role") ?? "volunteer",
                walletBalance: row.double("walletBalance"),
                walletCurrency: row.string("walletCurrency") ?? "USD"
            )
        }
    }

    func getSupportAccountDetails(
        userId: String,
        verificationEmail: String?,
        verificationPhone: String?
    ) async throws -> SupportAccountDetailsRecord {
        var payload: [String: Any] = ["userId": userId]
        if let verificationEmail, !verificationEmail.isEmpty {
            payload["verificationEmail"] = verificationEmail
        }
        if let verificationPhone, !verificationPhone.isEmpty {
            payload["verificationPhone"] = verificationPhone
        }

        let map = try await FunctionsService.shared.callMap(function: .supportGetUserAccountDetails, data: payload)
        let data = (map["data"] as? [String: Any]) ?? map
        let user = (data["user"] as? [String: Any]) ?? [:]
        let complaintsRaw = (data["complaints"] as? [Any]) ?? []
        let txRaw = (data["transactions"] as? [Any]) ?? []

        let complaints = complaintsRaw.compactMap { raw -> SupportComplaintRecord? in
            guard let row = coerceStringMap(raw) else { return nil }
            guard let id = row.string("reportId") else { return nil }
            return SupportComplaintRecord(
                id: id,
                reason: row.string("reasonForReport") ?? row.string("reason") ?? "No reason provided.",
                eventName: row.string("eventName"),
                reportedEmail: row.string("reportedUserEmail"),
                reporterDisplayName: row.string("reportingUserDisplayName"),
                timestamp: row.dateFromMillis("timestampMs")
            )
        }

        let transactions = txRaw.compactMap { raw -> SupportAccountTransactionRecord? in
            guard let row = coerceStringMap(raw) else { return nil }
            guard let id = row.string("transactionId") else { return nil }
            return SupportAccountTransactionRecord(
                id: id,
                title: row.string("title") ?? "Transaction",
                amount: row.double("amount"),
                type: row.string("type") ?? "UNKNOWN",
                status: row.string("status") ?? "UNKNOWN",
                source: row.string("source"),
                note: row.string("note"),
                timestamp: row.dateFromMillis("timestampMs")
            )
        }

        return SupportAccountDetailsRecord(
            userId: user.string("userId") ?? userId,
            username: user.string("username") ?? "Unknown",
            email: user.string("email") ?? "",
            phone: user.string("phone") ?? "",
            role: user.string("role") ?? "volunteer",
            walletBalance: user.double("walletBalance"),
            walletCurrency: user.string("walletCurrency") ?? "USD",
            payoutsEnabled: user.bool("payoutsEnabled"),
            chargesEnabled: user.bool("chargesEnabled"),
            detailsSubmitted: user.bool("detailsSubmitted"),
            complaints: complaints,
            transactions: transactions
        )
    }

    func addSupportAssociate(email: String) async throws {
        _ = try await FunctionsService.shared.call(
            function: .adminAddSupportAssociate,
            data: ["associateEmail": email]
        )
    }

    func fetchUserReports(limit: Int = 250) async throws -> [OwnerUserReportRecord] {
        var loadedSnapshots: [(String, QuerySnapshot)] = []
        var lastError: Error?

        do {
            let snapshot = try await db.collection("user_reports")
                .limit(to: limit)
                .getDocuments()
            loadedSnapshots.append(("user_reports", snapshot))
        } catch {
            lastError = error
        }

        do {
            let snapshot = try await db.collection("userReports")
                .limit(to: limit)
                .getDocuments()
            loadedSnapshots.append(("userReports", snapshot))
        } catch {
            lastError = error
        }

        if loadedSnapshots.isEmpty, let lastError {
            throw lastError
        }

        var merged: [String: OwnerUserReportRecord] = [:]
        for (collectionName, snapshot) in loadedSnapshots {
            for doc in snapshot.documents {
                let data = doc.data()
                let timestamp = (data["timestamp"] as? Timestamp)?.dateValue()
                    ?? (data["createdAt"] as? Timestamp)?.dateValue()
                    ?? data.dateFromMillis("timestampMs")
                    ?? data.dateFromMillis("createdAtMs")
                let report = OwnerUserReportRecord(
                    id: "\(collectionName):\(doc.documentID)",
                    sourceCollection: collectionName,
                    reportedUserName: (data["reportedUserName"] as? String)
                        ?? (data["reportedName"] as? String)
                        ?? (data["reportedUsername"] as? String)
                        ?? "Unknown",
                    reportedUserEmail: data["reportedUserEmail"] as? String,
                    eventName: data["eventName"] as? String,
                    reason: (data["reasonForReport"] as? String)
                        ?? (data["reason"] as? String)
                        ?? (data["message"] as? String)
                        ?? "No reason provided",
                    reportingUserDisplayName: (data["reportingUserDisplayName"] as? String)
                        ?? (data["reporterName"] as? String),
                    reportingUserId: (data["reportingUserId"] as? String)
                        ?? (data["reporterId"] as? String),
                    timestamp: timestamp
                )
                merged[report.id] = report
            }
        }

        return merged.values.sorted {
            let l = $0.timestamp ?? .distantPast
            let r = $1.timestamp ?? .distantPast
            return l > r
        }
    }

    func fetchKycUsers(limit: Int = 250) async throws -> [OwnerKYCRecord] {
        let snapshot = try await db.collection(FirestoreCollection.users.rawValue)
            .limit(to: limit)
            .getDocuments()

        return snapshot.documents.map { doc in
            let data = doc.data()
            return OwnerKYCRecord(
                id: doc.documentID,
                name: (data["name"] as? String) ?? (data["username"] as? String) ?? "Unknown",
                email: (data["email"] as? String) ?? "",
                role: (data["role"] as? String) ?? "volunteer",
                emailVerified: (data["emailVerified"] as? Bool) ?? false,
                profileStatus: (data["profileStatus"] as? String) ?? "unknown",
                updatedAt: (data["updatedAt"] as? Timestamp)?.dateValue() ?? (data["createdAt"] as? Timestamp)?.dateValue()
            )
        }
        .sorted {
            let l = $0.updatedAt ?? .distantPast
            let r = $1.updatedAt ?? .distantPast
            return l > r
        }
    }

    func fetchFeeSettings() async throws -> OwnerFeeSettingsRecord {
        let snapshot = try await db.collection(FirestoreCollection.appConfig.rawValue)
            .document("fee_settings")
            .getDocument()
        let data = snapshot.data() ?? [:]

        return OwnerFeeSettingsRecord(
            blindDateFeeUsd: data.double("blindDateFeeUsd", fallback: 10.0),
            agentAuthorizationFeeUsd: data.double("agentAuthorizationFeeUsd", fallback: 1.0),
            forexProfitMargin: data.double("forexProfitMargin", fallback: 0.010),
            stripeForexDepositProfitMargin: data.double("stripeForexDepositProfitMargin", fallback: 0.005),
            mobileMoneyHiddenFeeRate: data.double("mobileMoneyHiddenFeeRate", fallback: 0.0)
        )
    }

    func saveFeeSettings(_ settings: OwnerFeeSettingsRecord) async throws {
        try await db.collection(FirestoreCollection.appConfig.rawValue)
            .document("fee_settings")
            .setData(
                [
                    "blindDateFeeUsd": settings.blindDateFeeUsd,
                    "agentAuthorizationFeeUsd": settings.agentAuthorizationFeeUsd,
                    "forexProfitMargin": settings.forexProfitMargin,
                    "stripeForexDepositProfitMargin": settings.stripeForexDepositProfitMargin,
                    "mobileMoneyHiddenFeeRate": settings.mobileMoneyHiddenFeeRate,
                    "updatedAt": FieldValue.serverTimestamp()
                ],
                merge: true
            )
    }

    func fetchSystemConfig() async throws -> OwnerSystemConfigRecord {
        let snapshot = try await db.collection(FirestoreCollection.appConfig.rawValue)
            .document("system_config")
            .getDocument()
        let data = snapshot.data() ?? [:]

        return OwnerSystemConfigRecord(
            maintenanceMode: data.bool("maintenanceMode"),
            allowNewSignups: (data["allowNewSignups"] as? Bool) ?? true,
            enableBlindDate: (data["enableBlindDate"] as? Bool) ?? true,
            enableLiveStreams: (data["enableLiveStreams"] as? Bool) ?? true,
            maxUploadMb: Int(data.number("maxUploadMb", fallback: 10))
        )
    }

    func saveSystemConfig(_ config: OwnerSystemConfigRecord) async throws {
        try await db.collection(FirestoreCollection.appConfig.rawValue)
            .document("system_config")
            .setData(
                [
                    "maintenanceMode": config.maintenanceMode,
                    "allowNewSignups": config.allowNewSignups,
                    "enableBlindDate": config.enableBlindDate,
                    "enableLiveStreams": config.enableLiveStreams,
                    "maxUploadMb": config.maxUploadMb,
                    "updatedAt": FieldValue.serverTimestamp()
                ],
                merge: true
            )
    }
}

private func coerceStringMap(_ value: Any) -> [String: Any]? {
    if let map = value as? [String: Any] {
        return map
    }
    if let map = value as? [AnyHashable: Any] {
        return map.reduce(into: [String: Any]()) { acc, kv in
            acc[String(describing: kv.key)] = kv.value
        }
    }
    return nil
}

private extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        (self[key] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nonEmpty
    }

    func number(_ key: String, fallback: Double = 0) -> Double {
        if let number = self[key] as? NSNumber {
            return number.doubleValue
        }
        if let value = self[key] as? Double {
            return value
        }
        if let value = self[key] as? Int {
            return Double(value)
        }
        if let text = self[key] as? String, let parsed = Double(text.trimmingCharacters(in: .whitespacesAndNewlines)) {
            return parsed
        }
        return fallback
    }

    func double(_ key: String, fallback: Double = 0) -> Double {
        number(key, fallback: fallback)
    }

    func bool(_ key: String) -> Bool {
        if let value = self[key] as? Bool {
            return value
        }
        if let value = self[key] as? NSNumber {
            return value.intValue != 0
        }
        if let value = self[key] as? String {
            let normalized = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            return normalized == "true" || normalized == "1" || normalized == "yes"
        }
        return false
    }

    func dateFromMillis(_ key: String) -> Date? {
        let ms = number(key)
        guard ms > 0 else { return nil }
        return Date(timeIntervalSince1970: ms / 1000.0)
    }

    func dateFromEpochGuess(_ key: String) -> Date? {
        let raw = number(key)
        guard raw > 0 else { return nil }
        if raw > 1_000_000_000_000 {
            return Date(timeIntervalSince1970: raw / 1000.0)
        }
        return Date(timeIntervalSince1970: raw)
    }
}

private extension Optional where Wrapped == String {
    var nonEmpty: String? {
        guard let self, !self.isEmpty else { return nil }
        return self
    }
}
