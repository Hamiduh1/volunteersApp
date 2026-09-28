import SwiftUI
import FirebaseCore

@main
struct VolunteersAppiOSApp: App {
    @UIApplicationDelegateAdaptor(VolunteersAppDelegate.self) private var appDelegate
    @StateObject private var session = SessionManager()
    @StateObject private var incomingCalls = IncomingCallCoordinator()
    @StateObject private var callMedia = AgoraCallMediaClient()

    init() {
#if DEBUG
        if let options = FirebaseApp.app()?.options {
            let bundleId = Bundle.main.bundleIdentifier ?? "unknown.bundle"
            let apiPrefix = String(options.apiKey.prefix(8))
            print("Firebase iOS configured: projectId=\(options.projectID ?? "nil"), appId=\(options.googleAppID), bundleId=\(bundleId), apiKeyPrefix=\(apiPrefix)...")
        } else {
            print("Firebase iOS configure failed: default app options unavailable.")
        }
#endif
        StripePaymentsService.shared.configureIfPossible()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(session)
                .environmentObject(incomingCalls)
                .environmentObject(callMedia)
                .overlay {
                    IncomingCallPresentation()
                }
                .onAppear {
                    if let payload = IncomingCallPushRouter.consumePendingPayload() {
                        incomingCalls.handleNotificationPayload(payload)
                    }
                }
                .onReceive(NotificationCenter.default.publisher(for: IncomingCallPushRouter.notification)) { notification in
                    incomingCalls.handleNotificationPayload(notification.userInfo ?? [:])
                    _ = IncomingCallPushRouter.consumePendingPayload()
                }
        }
    }
}
