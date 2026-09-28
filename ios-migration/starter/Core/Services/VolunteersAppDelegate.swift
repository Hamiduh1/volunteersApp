import FirebaseCore
import FirebaseMessaging
import UIKit
import UserNotifications

enum IncomingCallPushRouter {
    static let notification = Notification.Name("IncomingCallPushRouter.notification")
    private static var pendingPayload: [AnyHashable: Any]?

    static func dispatch(_ userInfo: [AnyHashable: Any]) {
        guard let type = userInfo["type"] as? String,
              type == "incoming_call" || type == "call_cancelled" else { return }
        // At cold launch the app delegate receives the alert before SwiftUI installs its
        // NotificationCenter observer. Retain it so opening an incoming call never loses its id.
        pendingPayload = userInfo
        NotificationCenter.default.post(name: notification, object: nil, userInfo: userInfo)
    }

    static func consumePendingPayload() -> [AnyHashable: Any]? {
        defer { pendingPayload = nil }
        return pendingPayload
    }
}

final class VolunteersAppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            guard granted, error == nil else { return }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }
        if let remoteNotification = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            IncomingCallPushRouter.dispatch(remoteNotification)
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        Task { await PushTokenRegistrar.register(token: fcmToken) }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        IncomingCallPushRouter.dispatch(userInfo)
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        IncomingCallPushRouter.dispatch(response.notification.request.content.userInfo)
        completionHandler()
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        IncomingCallPushRouter.dispatch(userInfo)
        completionHandler(.newData)
    }
}
