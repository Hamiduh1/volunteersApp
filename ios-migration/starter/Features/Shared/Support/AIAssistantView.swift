import SwiftUI

struct AIAssistantView: View {
    @StateObject private var viewModel = AIAssistantViewModel()
    @FocusState private var queryFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Menu {
                    if viewModel.faqItems.isEmpty {
                        Text("No FAQs yet")
                    } else {
                        ForEach(viewModel.faqItems.prefix(10)) { item in
                            Button(item.question ?? "FAQ") {
                                viewModel.useSuggestion(item)
                                queryFocused = true
                            }
                        }
                    }
                } label: {
                    Label("FAQ", systemImage: "chevron.down.circle")
                }

                TextField("Ask AI about the app...", text: $viewModel.query)
                    .textFieldStyle(.roundedBorder)
                    .focused($queryFocused)

                Button("Send") {
                    viewModel.send()
                    queryFocused = true
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding()

            if let notice = viewModel.noticeMessage, !notice.isEmpty {
                Text(notice)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)
                    .padding(.bottom, 8)
            }

            if !viewModel.faqItems.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(viewModel.faqItems.prefix(6)) { item in
                            Button(item.question ?? "FAQ") {
                                viewModel.useSuggestion(item)
                                queryFocused = true
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 8)
                }
            }

            Divider()

            if viewModel.isLoading && viewModel.messages.isEmpty {
                Spacer()
                ProgressView("Loading AI support...")
                Spacer()
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            ForEach(viewModel.messages) { message in
                                HStack {
                                    if message.role == "user" { Spacer(minLength: 24) }
                                    Text(message.text)
                                        .padding(10)
                                        .background(message.role == "user" ? Color.blue.opacity(0.15) : Color.gray.opacity(0.15))
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                    if message.role != "user" { Spacer(minLength: 24) }
                                }
                                .id(message.id)
                            }
                        }
                        .padding()
                    }
                    .onChange(of: viewModel.messages.count) { _, _ in
                        if let lastId = viewModel.messages.last?.id {
                            withAnimation {
                                proxy.scrollTo(lastId, anchor: .bottom)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("AI Assistant")
        .task {
            await viewModel.loadFaq()
            queryFocused = true
        }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }
}
