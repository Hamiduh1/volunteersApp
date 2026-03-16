import SwiftUI
import FirebaseCore

@main
struct VolunteersAppiOSApp: App {
    @StateObject private var session = SessionManager()

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(session)
        }
    }
}
