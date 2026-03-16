import SwiftUI

struct AdvancedToolsHomeView: View {
    let user: AppSessionUser

    var body: some View {
        NavigationStack {
            List {
                Section("Communication") {
                    NavigationLink("Chat & Invitations") {
                        ConversationsListView(user: user)
                    }
                    NavigationLink("Call History") {
                        CallHistoryView(user: user)
                    }
                }

                Section("Live") {
                    NavigationLink("Live Sessions") {
                        LiveSessionsView(user: user)
                    }
                }

                Section("Payments") {
                    NavigationLink("Payment Methods & Payout") {
                        PaymentMethodsView(user: user)
                    }
                }

                Section("Help & Policies") {
                    NavigationLink("AI Assistant") {
                        AIAssistantView()
                    }
                    NavigationLink("Privacy Policy") {
                        PrivacyPolicyView()
                    }
                    NavigationLink("Terms & Conditions") {
                        TermsAndConditionsView()
                    }
                    NavigationLink("Support Center") {
                        SupportCenterView()
                    }
                }
            }
            .navigationTitle("Tools")
        }
    }
}
