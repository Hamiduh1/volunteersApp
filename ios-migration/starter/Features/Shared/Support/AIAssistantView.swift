import SwiftUI

struct AIAssistantView: View {
    @StateObject private var viewModel = AIAssistantViewModel()

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
                            }
                        }
                    }
                } label: {
                    Label("FAQ", systemImage: "chevron.down.circle")
                }

                TextField("Ask AI about the app...", text: $viewModel.query)
                    .textFieldStyle(.roundedBorder)

                Button("Send") {
                    viewModel.send()
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding()

            Divider()

            if viewModel.isLoading && viewModel.messages.isEmpty {
                Spacer()
                ProgressView("Loading AI support...")
                Spacer()
            } else {
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
                        }
                    }
                    .padding()
                }
            }
        }
        .navigationTitle("AI Assistant")
        .task { await viewModel.loadFaq() }
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
