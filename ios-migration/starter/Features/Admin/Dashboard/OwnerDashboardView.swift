import SwiftUI

struct OwnerDashboardView: View {
    private enum SectionAnchor {
        static let dashboard = "owner_dashboard_home"
        static let totals = "owner_totals"
        static let sources = "owner_sources"
        static let countries = "owner_countries"
        static let operations = "owner_operations"
        static let window = "owner_window"
        static let transactions = "owner_transactions"
    }

    let user: AppSessionUser
    private let embedded: Bool
    @StateObject private var viewModel = OwnerDashboardViewModel()

    init(user: AppSessionUser, embedded: Bool = false) {
        self.user = user
        self.embedded = embedded
    }

    private var totalEarnings: Double {
        if viewModel.summary.totalCollected > 0 { return viewModel.summary.totalCollected }
        return [
            viewModel.summary.stripeForexEarnings,
            viewModel.summary.mobileMoneyHiddenFee,
            viewModel.summary.blindDateFees,
            viewModel.summary.eventTicketOwnerFee,
            viewModel.summary.agentAuthorizationFees,
            viewModel.summary.agentCashoutOwnerShare,
            viewModel.summary.marketplacePlatinumFee,
            viewModel.summary.garageSaleFee,
            viewModel.summary.otherIncome
        ].reduce(0, +)
    }

    private var isOwner: Bool {
        user.role == .owner
    }

    var body: some View {
<<<<<<< HEAD
        Group {
            if embedded {
                dashboardContent
            } else {
                NavigationStack {
                    dashboardContent
=======
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 14) {
                        dashboardHomeCard(proxy: proxy)
                            .id(SectionAnchor.dashboard)

                        if let status = viewModel.statusMessage, !status.isEmpty {
                            Text(status)
                                .font(.subheadline)
                                .foregroundStyle(.green)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        AdminRoleResponsibilitiesCard(
                            screenKey: AdminRoleResponsibilitiesScreenKey.OWNER_DASHBOARD,
                            fallbackGuide: AdminRoleResponsibilitiesGuide(
                                roleTitle: "Platform Owner / Admin",
                                mission: "Keep operations safe, compliant, and financially accurate.",
                                responsibilities: [
                                    "Monitor revenue and country/source trends daily.",
                                    "Approve sensitive reversals and investigate anomalies quickly.",
                                    "Manage admin and associate access with least-privilege controls.",
                                    "Review fee/config changes before releasing to production."
                                ],
                                escalationRule: "Escalate fraud, legal disputes, or payout incidents immediately."
                            )
                        )

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Total Earnings")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(currency(totalEarnings))
                                .font(.largeTitle.weight(.bold))
                            Text("Balance: \(currency(viewModel.summary.balance))")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("\(viewModel.activeWindow.label) net revenue: \(currency(viewModel.filteredNetRevenue))")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Color.blue.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .id(SectionAnchor.totals)

                        GroupBox("Revenue Sources") {
                            VStack(spacing: 8) {
                                metricRow("Stripe FX", viewModel.summary.stripeForexEarnings)
                                metricRow("Mobile Hidden Fee", viewModel.summary.mobileMoneyHiddenFee)
                                metricRow("Blind Date Fees", viewModel.summary.blindDateFees)
                                metricRow("Agent Authorization", viewModel.summary.agentAuthorizationFees)
                                metricRow("Agent Cashout Share", viewModel.summary.agentCashoutOwnerShare)
                                metricRow("Other Income", viewModel.summary.otherIncome)
                            }
                            .padding(.top, 4)
                        }
                        .id(SectionAnchor.sources)

                        GroupBox("Country Revenue Explorer") {
                            VStack(alignment: .leading, spacing: 10) {
                                TextField("Search country", text: $viewModel.countrySearchText)
                                    .textFieldStyle(.roundedBorder)

                                if viewModel.isCountryAnalyticsLoading {
                                    ProgressView("Loading country revenue...")
                                        .font(.caption)
                                }

                                if viewModel.availableCountries.isEmpty {
                                    Text("No country-linked revenue transactions found yet.")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                } else {
                                    if viewModel.filteredCountryOptions.isEmpty {
                                        Text("No country matches that search.")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    } else {
                                        ScrollView(.horizontal, showsIndicators: false) {
                                            HStack(spacing: 8) {
                                                ForEach(Array(viewModel.filteredCountryOptions.prefix(8)), id: \.self) { country in
                                                    Button(country) {
                                                        viewModel.selectCountry(country)
                                                    }
                                                    .buttonStyle(country == viewModel.selectedCountry ? .borderedProminent : .bordered)
                                                    .font(.caption.weight(.semibold))
                                                }
                                            }
                                        }
                                    }

                                    if !viewModel.selectedCountry.isEmpty {
                                        Divider()
                                        Text("\(viewModel.selectedCountry) Total: \(currency(viewModel.selectedCountryTotal))")
                                            .font(.subheadline.weight(.semibold))

                                        if viewModel.selectedCountrySourceBreakdown.isEmpty {
                                            Text("No revenue sources available for this country.")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        } else {
                                            ForEach(viewModel.selectedCountrySourceBreakdown, id: \.source) { item in
                                                HStack {
                                                    Text(countryRevenueSourceLabel(item.source))
                                                    Spacer()
                                                    Text(currency(item.amount))
                                                        .fontWeight(.semibold)
                                                }
                                                .font(.caption)
                                            }
                                        }
                                    }

                                    if !viewModel.topCountries.isEmpty {
                                        Divider()
                                        Text("Top Countries")
                                            .font(.caption.weight(.semibold))
                                        ForEach(Array(viewModel.topCountries.enumerated()), id: \.element.id) { index, row in
                                            Text("\(index + 1). \(row.country): \(currency(row.total))")
                                                .font(.caption2)
                                        }
                                    }
                                }
                            }
                            .padding(.top, 4)
                        }
                        .id(SectionAnchor.countries)

                        GroupBox("Operations") {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Revenue events: \(viewModel.summary.transactionCount)")
                                Text("Last update: \(dateText(viewModel.summary.lastUpdate))")
                                    .foregroundStyle(.secondary)
                                Button {
                                    Task { await viewModel.cashOut() }
                                } label: {
                                    if viewModel.isCashingOut {
                                        ProgressView()
                                    } else {
                                        Text("Cash Out Owner Revenue")
                                    }
                                }
                                .buttonStyle(.borderedProminent)
                                .disabled(viewModel.summary.balance <= 0 || viewModel.isCashingOut)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .id(SectionAnchor.operations)

                        GroupBox("Transaction Window") {
                            VStack(alignment: .leading, spacing: 8) {
                                Picker("Range", selection: $viewModel.activeWindow) {
                                    ForEach(OwnerRevenueWindow.allCases) { window in
                                        Text(window.label).tag(window)
                                    }
                                }
                                .pickerStyle(.segmented)
                                Text("Showing \(viewModel.filteredTransactions.count) transaction(s)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .id(SectionAnchor.window)

                        GroupBox("Recent Revenue Transactions") {
                            if viewModel.isLoading && viewModel.filteredTransactions.isEmpty {
                                ProgressView("Loading revenue transactions...")
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else if viewModel.filteredTransactions.isEmpty {
                                Text("No revenue transactions yet.")
                                    .foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                VStack(spacing: 8) {
                                    ForEach(viewModel.filteredTransactions.prefix(12)) { tx in
                                        HStack {
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(tx.source.capitalized)
                                                    .font(.headline)
                                                if let note = tx.note, !note.isEmpty {
                                                    Text(note)
                                                        .font(.caption)
                                                        .foregroundStyle(.secondary)
                                                }
                                                Text(dateText(tx.createdAt))
                                                    .font(.caption2)
                                                    .foregroundStyle(.secondary)
                                            }
                                            Spacer()
                                            Text(currency(tx.amount))
                                                .fontWeight(.semibold)
                                                .foregroundStyle(tx.amount >= 0 ? .green : .red)
                                        }
                                        .padding(.vertical, 4)
                                    }
                                }
                            }
                        }
                        .id(SectionAnchor.transactions)
                    }
                    .padding()
                }
            }
                .navigationTitle("Owner Dashboard")
                .task { await viewModel.refresh() }
                .refreshable { await viewModel.refresh() }
                .alert("Error", isPresented: Binding(
                    get: { viewModel.errorMessage != nil },
                    set: { if !$0 { viewModel.errorMessage = nil } }
                )) {
                    Button("OK", role: .cancel) {}
                } message: {
                    Text(viewModel.errorMessage ?? "Unknown error")
>>>>>>> cc9d9a7 (iOS admin dashboard updates: country analytics, role responsibilities, fee config parity)
                }
            }
        }
    }

    @ViewBuilder
    private var dashboardContent: some View {
        ScrollView {
            VStack(spacing: 14) {
                dashboardHomeCard
                    .id(SectionAnchor.dashboard)

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Total Earnings")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(currency(totalEarnings))
                        .font(.largeTitle.weight(.bold))
                    Text("Balance: \(currency(viewModel.summary.balance))")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text("\(viewModel.activeWindow.label) net revenue: \(currency(viewModel.filteredNetRevenue))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Color.blue.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .id(SectionAnchor.totals)

                modernSectionCard(
                    title: "Revenue Sources",
                    subtitle: "Live breakdown of platform earnings",
                    icon: "chart.pie.fill",
                    accent: .orange
                ) {
                    VStack(spacing: 8) {
                        sourceMetricRow("Stripe FX", viewModel.summary.stripeForexEarnings, tint: .orange)
                        sourceMetricRow("Mobile Hidden Fee", viewModel.summary.mobileMoneyHiddenFee, tint: .mint)
                        sourceMetricRow("Blind Date Fees", viewModel.summary.blindDateFees, tint: .pink)
                        sourceMetricRow("Event Ticket Owner Fee", viewModel.summary.eventTicketOwnerFee, tint: .purple)
                        sourceMetricRow("Agent Authorization", viewModel.summary.agentAuthorizationFees, tint: .indigo)
                        sourceMetricRow("Agent Cashout Share", viewModel.summary.agentCashoutOwnerShare, tint: .teal)
                        sourceMetricRow("Marketplace Commission", viewModel.summary.marketplacePlatinumFee, tint: .yellow)
                        sourceMetricRow("Garage Sale Fee", viewModel.summary.garageSaleFee, tint: .brown)
                        sourceMetricRow("Other Income", viewModel.summary.otherIncome, tint: .blue)
                    }
                }
                .id(SectionAnchor.sources)

                modernSectionCard(
                    title: "Operations",
                    subtitle: "Owner controls for revenue workflows",
                    icon: "gearshape.2.fill",
                    accent: .green
                ) {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 8) {
                            operationPill(
                                title: "Events",
                                value: "\(viewModel.summary.transactionCount)",
                                tint: .green
                            )
                            operationPill(
                                title: "Window",
                                value: viewModel.activeWindow.label,
                                tint: .blue
                            )
                        }

                        Text("Last update: \(dateText(viewModel.summary.lastUpdate))")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        Button {
                            Task { await viewModel.cashOut() }
                        } label: {
                            HStack {
                                if viewModel.isCashingOut {
                                    ProgressView()
                                } else {
                                    Image(systemName: "banknote.fill")
                                    Text("Cash Out Owner Revenue")
                                }
                                Spacer()
                                Image(systemName: "arrow.right")
                                    .font(.caption.weight(.bold))
                                    .opacity(viewModel.isCashingOut ? 0 : 1)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.summary.balance <= 0 || viewModel.isCashingOut)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .id(SectionAnchor.operations)

                modernSectionCard(
                    title: "Task Links",
                    subtitle: "Moderation and compliance workflows",
                    icon: "square.grid.2x2.fill",
                    accent: .blue
                ) {
                    VStack(alignment: .leading, spacing: 10) {
                        dashboardRouteRow(
                            title: "Payout Queue",
                            subtitle: "Review and reverse payout requests",
                            icon: "tray.full.fill"
                        ) {
                            AdminPayoutQueueView(user: user)
                        }

                        dashboardRouteRow(
                            title: "Deposit Queue",
                            subtitle: "Review card and bank deposit requests",
                            icon: "tray.and.arrow.down.fill"
                        ) {
                            AdminDepositQueueView(user: user)
                        }

                        dashboardRouteRow(
                            title: "Associates",
                            subtitle: "Search users and support operations",
                            icon: "person.2.badge.gearshape"
                        ) {
                            SupportConsoleView(user: user, embedded: true)
                        }

                        dashboardRouteRow(
                            title: "Disputes / Reports",
                            subtitle: "Review user complaints and abuse reports",
                            icon: "exclamationmark.bubble.fill"
                        ) {
                            OwnerUserReportsView()
                        }

                        dashboardRouteRow(
                            title: "KYC",
                            subtitle: "Inspect verification and profile status",
                            icon: "person.text.rectangle.fill"
                        ) {
                            OwnerKYCReviewView()
                        }

                        dashboardRouteRow(
                            title: "Fee Settings",
                            subtitle: "Adjust revenue and fee configuration",
                            icon: "slider.horizontal.3"
                        ) {
                            OwnerFeeSettingsView()
                        }

                        dashboardRouteRow(
                            title: "System Config",
                            subtitle: "Toggle global operational controls",
                            icon: "server.rack"
                        ) {
                            OwnerSystemConfigView()
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

<<<<<<< HEAD
                if isOwner {
                    GroupBox("Access Management") {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Grant admin by email")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField("user@example.com", text: $viewModel.adminGrantEmail)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .keyboardType(.emailAddress)
                            Button {
                                Task { await viewModel.grantAdminAccess(ownerId: user.uid) }
                            } label: {
                                if viewModel.isGrantingAdmin {
                                    ProgressView()
                                } else {
                                    Text("Grant Admin Access")
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(viewModel.adminGrantEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isGrantingAdmin)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
=======
                HStack(spacing: 8) {
                    dashboardQuickButton("Totals") {
                        withAnimation { proxy.scrollTo(SectionAnchor.totals, anchor: .top) }
                    }
                    dashboardQuickButton("Sources") {
                        withAnimation { proxy.scrollTo(SectionAnchor.sources, anchor: .top) }
                    }
                    dashboardQuickButton("Country") {
                        withAnimation { proxy.scrollTo(SectionAnchor.countries, anchor: .top) }
                    }
                }

                HStack(spacing: 8) {
                    dashboardQuickButton("Ops") {
                        withAnimation { proxy.scrollTo(SectionAnchor.operations, anchor: .top) }
                    }
                    dashboardQuickButton("Tx") {
                        withAnimation { proxy.scrollTo(SectionAnchor.transactions, anchor: .top) }
>>>>>>> cc9d9a7 (iOS admin dashboard updates: country analytics, role responsibilities, fee config parity)
                    }
                }
                GroupBox("Transaction Window") {
                    VStack(alignment: .leading, spacing: 8) {
                        Picker("Range", selection: $viewModel.activeWindow) {
                            ForEach(OwnerRevenueWindow.allCases) { window in
                                Text(window.label).tag(window)
                            }
                        }
                        .pickerStyle(.segmented)
                        Text("Showing \(viewModel.filteredTransactions.count) transaction(s)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .id(SectionAnchor.window)

                GroupBox("Recent Revenue Transactions") {
                    if viewModel.isLoading && viewModel.filteredTransactions.isEmpty {
                        ProgressView("Loading revenue transactions...")
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else if viewModel.filteredTransactions.isEmpty {
                        Text("No revenue transactions yet.")
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        VStack(spacing: 8) {
                            ForEach(viewModel.filteredTransactions.prefix(12)) { tx in
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(tx.source.capitalized)
                                            .font(.headline)
                                        if let note = tx.note, !note.isEmpty {
                                            Text(note)
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                        Text(dateText(tx.createdAt))
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(currency(tx.amount))
                                        .fontWeight(.semibold)
                                        .foregroundStyle(tx.amount >= 0 ? .green : .red)
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    }
                }
                .id(SectionAnchor.transactions)
            }
            .padding()
        }
        .navigationTitle("Owner Dashboard")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    @ViewBuilder
    private var dashboardHomeCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Owner Command Center")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                    Text("Monitor revenue and run operational actions quickly.")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.84))
                }
                Spacer()
                Image(systemName: "chart.line.uptrend.xyaxis.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.white.opacity(0.9))
            }

            HStack(spacing: 8) {
                dashboardMetric(value: currency(totalEarnings), label: "Total")
                dashboardMetric(value: currency(viewModel.summary.balance), label: "Balance")
                dashboardMetric(value: "\(viewModel.summary.transactionCount)", label: "Events")
            }

            HStack(spacing: 10) {
                dashboardTile(
                    title: "Refresh",
                    subtitle: "Sync latest revenue",
                    icon: "arrow.clockwise.circle.fill",
                    tint: .blue
                ) {
                    Task { await viewModel.refresh() }
                }

                dashboardTile(
                    title: "Cash Out",
                    subtitle: "Transfer available balance",
                    icon: "banknote.fill",
                    tint: .green
                ) {
                    Task { await viewModel.cashOut() }
                }
                .disabled(viewModel.summary.balance <= 0 || viewModel.isCashingOut)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Quick Links")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.82))

                HStack(spacing: 8) {
                    NavigationLink {
                        AdminControlsHubView(user: user, embedded: true)
                    } label: {
                        dashboardShortcutLabel(title: "Admin Controls", icon: "switch.2")
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        SupportConsoleView(user: user, embedded: true)
                    } label: {
                        dashboardShortcutLabel(title: "Support", icon: "person.2.badge.gearshape")
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        GlobalWalletHomeView(user: user)
                    } label: {
                        dashboardShortcutLabel(title: "Wallet", icon: "wallet.pass.fill")
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 0.06, green: 0.15, blue: 0.24),
                            Color(red: 0.10, green: 0.29, blue: 0.40)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color.white.opacity(0.18), lineWidth: 1)
        )
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.78))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.white.opacity(0.14))
        )
    }

    @ViewBuilder
    private func dashboardTile(
        title: String,
        subtitle: String,
        icon: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(
                        Circle()
                            .fill(tint.opacity(0.18))
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.75))
                        .lineLimit(1)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.white.opacity(0.7))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 11)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.white.opacity(0.12))
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func dashboardShortcutLabel(title: String, icon: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption.weight(.bold))
            Text(title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.white.opacity(0.12))
        )
    }

    @ViewBuilder
    private func sourceMetricRow(_ label: String, _ value: Double, tint: Color) -> some View {
        HStack {
            HStack(spacing: 8) {
                Circle()
                    .fill(tint.opacity(0.8))
                    .frame(width: 8, height: 8)
                Text(label)
                    .font(.subheadline)
            }
            Spacer()
            Text(currency(value))
                .fontWeight(.semibold)
                .foregroundStyle(.primary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func operationPill(title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(tint.opacity(0.12))
        )
    }

    @ViewBuilder
    private func modernSectionCard<Content: View>(
        title: String,
        subtitle: String,
        icon: String,
        accent: Color,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                HStack(spacing: 8) {
                    Image(systemName: icon)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(.headline)
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
            }

            content()
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(accent.opacity(0.16), lineWidth: 1)
        )
    }

    @ViewBuilder
    private func dashboardRouteRow<Destination: View>(
        title: String,
        subtitle: String,
        icon: String,
        @ViewBuilder destination: () -> Destination
    ) -> some View {
        NavigationLink {
            destination()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.blue)
                    .frame(width: 30, height: 30)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color.blue.opacity(0.14))
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
        }
        .buttonStyle(.plain)
    }

    private func currency(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = "USD"
        return f.string(from: NSNumber(value: value)) ?? "$0.00"
    }

    private func dateText(_ date: Date?) -> String {
        guard let date else { return "--" }
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    private func countryRevenueSourceLabel(_ source: String) -> String {
        switch source {
        case "stripeForexEarnings":
            return "Stripe FX 0.5%"
        case "mobileMoneyHiddenFee":
            return "Hidden Mobile Money Fee"
        case "blindDateFees":
            return "Blind Date Fees"
        case "agentAuthorizationFees":
            return "Agent Authorization"
        case "agentCashoutOwnerShare":
            return "Agent Cashout Owner Share"
        case "eventTicketOwnerFee":
            return "Event Ticket Owner Fee"
        case "advertisementFees":
            return "Sponsored Ad Fee"
        case "marketplacePlatinumFee":
            return "Marketplace Platinum Fee"
        case "garageSaleFee":
            return "Garage Sale Fee"
        case "otherIncome":
            return "Other Income"
        default:
            return source.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }
}
