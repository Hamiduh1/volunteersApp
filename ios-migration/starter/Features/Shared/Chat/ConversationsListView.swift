import SwiftUI

enum SocialInboxTab: String, CaseIterable, Identifiable {
    case chats
    case invitations
    case calls

    var id: String { rawValue }
}

struct ConversationsListView: View {
    private enum SectionAnchor {
        static let dashboard = "social_dashboard"
        static let filters = "social_filters"
        static let chats = "social_chats"
        static let invitations = "social_invitations"
        static let calls = "social_calls"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = ConversationsListViewModel()
    @StateObject private var callsViewModel = CallHistoryViewModel()
    @State private var selectedTab: SocialInboxTab = .chats

    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    dashboardHomeSection(proxy: proxy)
                }
                .id(SectionAnchor.dashboard)

                Section {
                    Picker("Inbox", selection: $selectedTab) {
                        Text("Chats").tag(SocialInboxTab.chats)
                        Text("Invitations (\(viewModel.pendingInvitationCount))").tag(SocialInboxTab.invitations)
                        Text("Calls (\(callsViewModel.missedCount) missed)").tag(SocialInboxTab.calls)
                    }
                    .pickerStyle(.segmented)

                    searchField

                    if selectedTab == .invitations {
                        Picker("Invitation Status", selection: $viewModel.invitationFilter) {
                            ForEach(InvitationStatusFilter.allCases) { filter in
                                Text(filter.title).tag(filter)
                            }
                        }
                        .pickerStyle(.segmented)
                    }

                    if selectedTab == .calls {
                        Picker("Type", selection: $callsViewModel.selectedFilter) {
                            ForEach(CallHistoryFilter.allCases) { filter in
                                Text(filter.title).tag(filter)
                            }
                        }
                        .pickerStyle(.segmented)

                        Picker("Direction", selection: $callsViewModel.directionFilter) {
                            ForEach(CallDirectionFilter.allCases) { filter in
                                Text(filter.title).tag(filter)
                            }
                        }
                        .pickerStyle(.menu)
                    }

                    Text(countSummary)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .id(SectionAnchor.filters)

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Section {
                        Text(status)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                if let status = callsViewModel.statusMessage, !status.isEmpty, selectedTab == .calls {
                    Section {
                        Text(status)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                switch selectedTab {
                case .chats:
                    chatsSection
                        .id(SectionAnchor.chats)
                case .invitations:
                    invitationsSection
                        .id(SectionAnchor.invitations)
                case .calls:
                    callsSection
                        .id(SectionAnchor.calls)
                }
            }
        }
        .navigationTitle("Social Inbox")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    UserDirectoryView(user: user)
                } label: {
                    Label("Find People", systemImage: "person.2")
                }
            }
        }
        .task {
            async let conv = viewModel.refresh(user: user)
            async let calls = callsViewModel.refresh(user: user)
            _ = await (conv, calls)
        }
        .refreshable {
            async let conv = viewModel.refresh(user: user)
            async let calls = callsViewModel.refresh(user: user)
            _ = await (conv, calls)
        }
        .navigationDestination(item: $viewModel.routeToConversation) { route in
            ConversationDetailView(user: user, conversationId: route.id)
        }
        .alert("Error", isPresented: Binding(
            get: { activeErrorMessage != nil },
            set: { if !$0 { clearErrors() } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(activeErrorMessage ?? "Unknown error")
        }
    }

    @ViewBuilder
    private func dashboardHomeSection(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Dashboard Home")
                .font(.headline)

            Text("Android-style Social Inbox navigation for chats, invitations, and calls.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                dashboardMetric(value: "\(viewModel.conversations.count)", label: "Chats")
                dashboardMetric(value: "\(viewModel.pendingInvitationCount)", label: "Pending")
                dashboardMetric(value: "\(callsViewModel.missedCount)", label: "Missed")
            }

            HStack(spacing: 10) {
                dashboardTile(title: "Chats", icon: "bubble.left.and.bubble.right.fill") {
                    // Keep existing route pattern and open User Directory from dashboard.
                    selectedTab = .chats
                    DispatchQueue.main.async {
                        withAnimation { proxy.scrollTo(SectionAnchor.chats, anchor: .top) }
                    }
                }
                NavigationLink {
                    UserDirectoryView(user: user)
                } label: {
                    dashboardTileLabel(title: "Directory", icon: "person.crop.circle.badge.plus")
                }
                .buttonStyle(.plain)
                dashboardTile(title: "Refresh", icon: "arrow.clockwise.circle.fill") {
                    Task {
                        async let conv = viewModel.refresh(user: user)
                        async let calls = callsViewModel.refresh(user: user)
                        _ = await (conv, calls)
                    }
                }
            }

            HStack(spacing: 8) {
                dashboardQuickButton("Filters") {
                    withAnimation { proxy.scrollTo(SectionAnchor.filters, anchor: .top) }
                }
                dashboardQuickButton("Chats") {
                    selectedTab = .chats
                    DispatchQueue.main.async {
                        withAnimation { proxy.scrollTo(SectionAnchor.chats, anchor: .top) }
                    }
                }
                dashboardQuickButton("Invites") {
                    selectedTab = .invitations
                    DispatchQueue.main.async {
                        withAnimation { proxy.scrollTo(SectionAnchor.invitations, anchor: .top) }
                    }
                }
                dashboardQuickButton("Calls") {
                    selectedTab = .calls
                    DispatchQueue.main.async {
                        withAnimation { proxy.scrollTo(SectionAnchor.calls, anchor: .top) }
                    }
                }
            }

            HStack(spacing: 8) {
                NavigationLink {
                    CommunityHubView(user: user)
                } label: {
                    Label("Community Dashboard", systemImage: "square.grid.2x2.fill")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)

                NavigationLink {
                    AIAssistantView()
                } label: {
                    Label("AI Assistant", systemImage: "sparkles")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.bordered)
            }
        }
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardTile(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            dashboardTileLabel(title: title, icon: icon)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func dashboardTileLabel(title: String, icon: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.blue)
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardQuickButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .buttonStyle(.bordered)
            .font(.caption.weight(.semibold))
    }

    private var searchField: some View {
        Group {
            switch selectedTab {
            case .calls:
                TextField("Search name, status, type", text: $callsViewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            case .invitations:
                TextField("Search invitations", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            case .chats:
                TextField("Search chats", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
        }
    }

    @ViewBuilder
    private var chatsSection: some View {
        Section("Chats") {
            if viewModel.isLoading && viewModel.filteredConversations.isEmpty {
                ProgressView("Loading conversations...")
            } else if viewModel.filteredConversations.isEmpty {
                Text("No conversations yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.filteredConversations) { conversation in
                    chatRow(conversation)
                }
            }
        }
    }

    @ViewBuilder
    private var invitationsSection: some View {
        Section("Invitations") {
            if viewModel.isLoading && viewModel.filteredInvitations.isEmpty {
                ProgressView("Loading invitations...")
            } else if viewModel.filteredInvitations.isEmpty {
                Text("No invitations found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.filteredInvitations) { invite in
                    let invitationId = invite.id ?? (invite.senderId ?? "")
                    let isUpdating = viewModel.updatingInvitationIds.contains(invitationId)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(invite.senderName ?? invite.inviterName ?? "Unknown Sender")
                            .font(.headline)

                        if let email = invite.senderEmail, !email.isEmpty {
                            Text(email)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        let source = (invite.source ?? "chat").replacingOccurrences(of: "_", with: " ")
                        let statusText = (invite.status ?? "pending").capitalized
                        Text("\(source.capitalized) - \(statusText)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)

                        if let context = invite.context, !context.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Text(context)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }

                        if (invite.status ?? "pending").lowercased() == "pending" {
                            HStack(spacing: 8) {
                                Button("Accept") {
                                    Task { await viewModel.acceptInvitation(invite, user: user) }
                                }
                                .buttonStyle(.borderedProminent)
                                .disabled(isUpdating)

                                Button("Decline") {
                                    Task { await viewModel.declineInvitation(invite, user: user) }
                                }
                                .buttonStyle(.bordered)
                                .disabled(isUpdating)

                                if isUpdating {
                                    ProgressView()
                                        .controlSize(.small)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    @ViewBuilder
    private var callsSection: some View {
        Section("Calls") {
            if callsViewModel.isLoading && callsViewModel.filteredCallLogs.isEmpty {
                ProgressView("Loading call history...")
            } else if callsViewModel.filteredCallLogs.isEmpty {
                Text("No call records yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(callsViewModel.filteredCallLogs) { call in
                    callRow(call)
                }
            }
        }
    }

    @ViewBuilder
    private func chatRow(_ conversation: ChatConversationRecord) -> some View {
        let conversationId = conversation.id ?? ""
        let displayName = conversation.otherParticipantName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = (displayName?.isEmpty == false ? displayName! : "Conversation")
        let isAudioLoading = viewModel.isInitiatingCall(conversationId: conversation.id, isVideo: false)
        let isVideoLoading = viewModel.isInitiatingCall(conversationId: conversation.id, isVideo: true)

        VStack(alignment: .leading, spacing: 8) {
            NavigationLink {
                ConversationDetailView(user: user, conversationId: conversationId)
            } label: {
                HStack(spacing: 10) {
                    avatar(urlString: conversation.otherParticipantProfilePicUrl, fallback: "person.circle.fill")
                    VStack(alignment: .leading, spacing: 4) {
                        Text(name)
                            .font(.headline)
                            .lineLimit(1)
                        Text(conversation.lastMessageText ?? conversation.lastMessage ?? "No messages yet")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        if let lastCall = formattedLastCall(conversation) {
                            Text(lastCall)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    if let ts = conversation.lastMessageTimestamp?.dateValue() {
                        Text(ts.formatted(date: .omitted, time: .shortened))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .disabled(conversationId.isEmpty)

            HStack(spacing: 8) {
                Button {
                    Task {
                        await viewModel.startCall(
                            conversation: conversation,
                            user: user,
                            isVideo: false
                        )
                    }
                } label: {
                    Label(isAudioLoading ? "Calling..." : "Call", systemImage: "phone")
                }
                .buttonStyle(.bordered)
                .disabled(conversationId.isEmpty || isAudioLoading || isVideoLoading)

                Button {
                    Task {
                        await viewModel.startCall(
                            conversation: conversation,
                            user: user,
                            isVideo: true
                        )
                    }
                } label: {
                    Label(isVideoLoading ? "Starting..." : "Video", systemImage: "video")
                }
                .buttonStyle(.bordered)
                .disabled(conversationId.isEmpty || isAudioLoading || isVideoLoading)
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func callRow(_ call: CallLogRecord) -> some View {
        let isVideo = (call.type ?? call.callType ?? "").lowercased() == "video"
        let isRedialLoading = viewModel.isInitiatingCall(conversationId: call.chatId, isVideo: isVideo)
        let icon = isVideo ? "video" : "phone"

        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(.secondary)

            VStack(alignment: .leading, spacing: 4) {
                Text(call.peerName ?? "Unknown user")
                    .font(.headline)
                Text(callSummary(call))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if let startedAt = call.startedAt?.dateValue() {
                Text(startedAt.formatted(date: .abbreviated, time: .shortened))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Button {
                Task {
                    await viewModel.startCallFromLog(
                        conversationId: call.chatId,
                        peerUid: call.peerUid,
                        user: user,
                        isVideo: isVideo
                    )
                }
            } label: {
                Image(systemName: "arrow.clockwise")
            }
            .buttonStyle(.borderless)
            .disabled(isRedialLoading)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func avatar(urlString: String?, fallback: String) -> some View {
        if let urlString, let url = URL(string: urlString), !urlString.isEmpty {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                default:
                    Image(systemName: fallback)
                        .resizable()
                        .scaledToFit()
                        .padding(6)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 44, height: 44)
            .clipShape(Circle())
        } else {
            Image(systemName: fallback)
                .resizable()
                .scaledToFit()
                .padding(6)
                .frame(width: 44, height: 44)
                .foregroundStyle(.secondary)
                .background(Circle().fill(Color(uiColor: .tertiarySystemFill)))
        }
    }

    private var countSummary: String {
        switch selectedTab {
        case .chats:
            return "Showing \(viewModel.filteredConversations.count) of \(viewModel.conversations.count) chats"
        case .invitations:
            return "Showing \(viewModel.filteredInvitations.count) invitations"
        case .calls:
            return "Showing \(callsViewModel.filteredCallLogs.count) of \(callsViewModel.callLogs.count) calls"
        }
    }

    private var activeErrorMessage: String? {
        viewModel.errorMessage ?? callsViewModel.errorMessage
    }

    private func clearErrors() {
        viewModel.errorMessage = nil
        callsViewModel.errorMessage = nil
    }

    private func formattedLastCall(_ conversation: ChatConversationRecord) -> String? {
        let status = (conversation.lastCallStatus ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let type = (conversation.lastCallType ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !status.isEmpty || !type.isEmpty else { return nil }

        let normalizedType = type.isEmpty ? "call" : type
        if let timestamp = conversation.lastCallTimestamp?.dateValue() {
            return "\(normalizedType.capitalized) \(status.capitalized) - \(timestamp.formatted(date: .omitted, time: .shortened))"
        }
        return "\(normalizedType.capitalized) \(status.capitalized)"
    }

    private func callSummary(_ call: CallLogRecord) -> String {
        let directionRaw = (call.direction ?? "unknown")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let directionLabel: String
        switch directionRaw {
        case "incoming":
            directionLabel = "Received"
        case "outgoing":
            directionLabel = "Dialed"
        case "missed":
            directionLabel = "Missed"
        default:
            directionLabel = directionRaw.capitalized
        }

        let status = (call.status ?? "unknown")
            .replacingOccurrences(of: "_", with: " ")
            .capitalized
        let type = ((call.type ?? call.callType ?? "audio")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased() == "video") ? "Video" : "Voice"
        return "\(directionLabel) \(type) - \(status)"
    }
}
