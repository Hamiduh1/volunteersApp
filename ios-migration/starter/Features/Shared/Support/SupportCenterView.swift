import SwiftUI

struct SupportCenterView: View {
    @StateObject private var viewModel = SupportCenterViewModel()
    @Environment(\.openURL) private var openURL

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            if viewModel.isLoading && viewModel.items.isEmpty {
                ProgressView("Loading support...")
            } else {
                ForEach(viewModel.items) { item in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(item.title ?? "Support")
                            .font(.headline)
                        if let value = item.value, !value.isEmpty {
                            Text(value)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }

                        actionView(for: item)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Support")
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
    private func actionView(for item: SupportItemRecord) -> some View {
        let normalizedType = normalizedSupportType(item)
        switch normalizedType {
        case "phone":
            Button("Call") {
                if let value = item.value, let url = telURL(from: value) {
                    openURL(url)
                }
            }
            .buttonStyle(.bordered)
        case "email":
            Button("Email") {
                if let value = item.value, let url = emailURL(from: value) {
                    openURL(url)
                }
            }
            .buttonStyle(.bordered)
        case "url", "link":
            if let value = item.value, let url = URL(string: value) {
                Link("Open Link", destination: url)
                    .font(.subheadline)
            }
        case "chat", "live_chat":
            NavigationLink("Open Live Chat") {
                AIAssistantView()
            }
        default:
            EmptyView()
        }
    }

    private func normalizedSupportType(_ item: SupportItemRecord) -> String {
        let explicit = (item.type ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if !explicit.isEmpty { return explicit }

        let title = (item.title ?? "").lowercased()
        if title.contains("phone") { return "phone" }
        if title.contains("email") { return "email" }
        if title.contains("chat") { return "chat" }
        if title.contains("link") || title.contains("website") { return "url" }
        return ""
    }

    private func telURL(from raw: String) -> URL? {
        let digits = raw.filter { "0123456789+".contains($0) }
        guard !digits.isEmpty else { return nil }
        return URL(string: "tel://\(digits)")
    }

    private func emailURL(from raw: String) -> URL? {
        let email = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !email.isEmpty else { return nil }
        return URL(string: "mailto:\(email)")
    }
}
