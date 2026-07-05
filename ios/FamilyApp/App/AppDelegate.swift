// Push wiring — the iOS twin of FamilyMessagingService + NotificationHelper:
// FCM registration (APNs → FCM token → device_push_tokens with platform='ios'),
// notification categories with inline chat reply, tap → familyapp://chat deep link,
// and foreground suppression for the currently open conversation.
import FirebaseCore
import FirebaseMessaging
import Supabase
import UIKit
import UserNotifications

enum NotificationCategory {
    static let message = "MESSAGE"
    static let birthday = "BIRTHDAY"
    static let event = "EVENT"
    static let replyAction = "REPLY"
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Firebase only activates when GoogleService-Info.plist is bundled (gitignored;
        // downloaded from the Firebase console — see supabase/functions/README.md).
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
            application.registerForRemoteNotifications()
            Task { @MainActor in
                FamilyRepository.shared.pushTokenProvider = {
                    try? await Messaging.messaging().token()
                }
            }
        }

        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.setNotificationCategories(Self.categories)
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    /// The 3 channels from Android's NotificationHelper, as UNNotificationCategories.
    private static var categories: Set<UNNotificationCategory> {
        let reply = UNTextInputNotificationAction(
            identifier: NotificationCategory.replyAction,
            title: "Reply",
            options: [],
            textInputButtonTitle: "Send",
            textInputPlaceholder: "Message…"
        )
        return [
            UNNotificationCategory(
                identifier: NotificationCategory.message,
                actions: [reply],
                intentIdentifiers: []
            ),
            UNNotificationCategory(identifier: NotificationCategory.birthday, actions: [], intentIdentifiers: []),
            UNNotificationCategory(identifier: NotificationCategory.event, actions: [], intentIdentifiers: []),
        ]
    }
}

// MARK: - FCM token

extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { @MainActor in
            await FamilyRepository.shared.registerPushToken(fcmToken)
        }
    }
}

// MARK: - Notification presentation + taps

extension AppDelegate: UNUserNotificationCenterDelegate {
    /// Foreground presentation: suppress the banner for the conversation that is
    /// currently open (ActiveChat parity); everything else shows normally.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        let payload = notification.request.content.userInfo
        let conversationId = payload["conversationId"] as? String
        let isOpenConversation = await MainActor.run {
            conversationId != nil && ActiveChat.conversationId == conversationId
        }
        return isOpenConversation ? [] : [.banner, .sound, .badge]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let payload = response.notification.request.content.userInfo
        guard let conversationId = payload["conversationId"] as? String else { return }

        if let textResponse = response as? UNTextInputNotificationResponse {
            // Inline reply straight through Postgrest (ReplyReceiver parity).
            await sendInlineReply(conversationId: conversationId, text: textResponse.userText)
            return
        }

        // Tap → open the thread via the same deep link the Android app uses.
        await MainActor.run {
            _ = UIApplication.shared // keep main-actor context explicit
            NotificationCenter.default.post(
                name: .openConversationDeepLink,
                object: nil,
                userInfo: ["conversationId": conversationId]
            )
        }
    }

    private func sendInlineReply(conversationId: String, text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let userId = await MainActor.run { SessionStore.shared.currentUserId }
        guard let userId else { return }
        try? await SupabaseClientProvider.client.from("messages").insert([
            "conversation_id": AnyJSON.string(conversationId),
            "user_from": .string(userId),
            "text": .string(trimmed),
            "message_type": .string("text"),
        ]).execute()
    }
}

extension Notification.Name {
    static let openConversationDeepLink = Notification.Name("openConversationDeepLink")
}

/// The conversation currently on screen — lets pushes for it be suppressed
/// (twin of Android's ActiveChat object).
@MainActor
enum ActiveChat {
    static var conversationId: String?
}
