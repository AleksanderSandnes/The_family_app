@testable import FamilyApp

// Shared fixtures for screen render tests: a MockRepository filled with the fictional
// Nordmann family, and a renderer that hosts a SwiftUI view in a real window so its body,
// lazy rows and `.task`/`.onAppear` loads all execute.
import SwiftUI
import UIKit
import XCTest

@MainActor
enum DemoFamily {
    static let familyId = "fam-1"
    static let emma = member("emma", "Emma Nordmann", birthday: "1988-04-12")
    static let lars = member("lars", "Lars Nordmann", birthday: "1986-09-03")
    static let nora = member("nora", "Nora Nordmann", birthday: "2014-06-21")

    static func member(_ id: String, _ name: String, birthday: String) -> UserModel {
        var user = UserModel()
        user.id = id
        user.name = name
        user.email = "\(id)@example.com"
        user.birthday = birthday
        user.familyId = familyId
        user.avatarColor = FamilyRepository.palette(name)
        user.lastActiveAt = isoNow()
        return user
    }

    static var today: String {
        LocalDate.today().description
    }

    /// A signed-in mock whose every canned read returns fictional family data.
    static func mock() -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: emma.id)
        for user in [emma, lars, nora] {
            mock.users[user.id] = user
        }
        mock.familyMembers = [emma, lars, nora]
        mock.usersByIdResult = [nora]
        mock.families[familyId] = FamilyModel(
            id: familyId,
            name: "Family Nordmann",
            joinCode: "DEMO42",
            adminId: emma.id
        )
        mock.relations = [lars.id: "Husband", nora.id: "Daughter"]
        mock.isAdminResult = true
        fillChat(mock)
        fillLists(mock)
        fillPlanning(mock)
        mock.userLocationsResult = [
            UserLocationModel(
                userId: lars.id,
                familyId: familyId,
                lat: 59.9139,
                lng: 10.7522,
                displayName: "Lars",
                visible: true
            ),
        ]
        return mock
    }

    private static func fillChat(_ mock: MockRepository) {
        let group = ConversationModel(id: "chat-1", userFrom: emma.id, name: "Family Nordmann", familyId: familyId)
        let direct = ConversationModel(id: "chat-2", userFrom: emma.id, userTo: lars.id)
        mock.conversationsResult = [group, direct]
        let messages = [
            MessageModel(
                id: "m1",
                conversationId: "chat-1",
                userFrom: lars.id,
                text: "Groceries on the way home?",
                sentAt: isoNow()
            ),
            MessageModel(
                id: "m2",
                conversationId: "chat-1",
                userFrom: emma.id,
                text: "Oat milk, please!",
                sentAt: isoNow(),
                replyToId: "m1"
            ),
            MessageModel(
                id: "m3",
                conversationId: "chat-1",
                userFrom: nora.id,
                text: "",
                sentAt: isoNow(),
                messageType: "image",
                mediaUrl: "https://fictional.example/taco.jpg"
            ),
            MessageModel(
                id: "m4",
                conversationId: "chat-1",
                userFrom: lars.id,
                text: "",
                sentAt: isoNow(),
                messageType: "voice",
                mediaUrl: "https://fictional.example/v.m4a"
            ),
            MessageModel(
                id: "m5",
                conversationId: "chat-1",
                userFrom: emma.id,
                text: "Lars joined",
                sentAt: isoNow(),
                messageType: "system"
            ),
            MessageModel(
                id: "m6",
                conversationId: "chat-1",
                userFrom: emma.id,
                text: "Edited plan",
                sentAt: isoNow(),
                editedAt: isoNow()
            ),
        ]
        mock.messagesByConversation = ["chat-1": messages, "chat-2": [messages[0]]]
        mock.lastMessageByConversation = ["chat-1": messages[1], "chat-2": messages[0]]
        let participants = [emma, lars, nora].map {
            ConversationParticipantModel(
                id: "p-\($0.id)",
                conversationId: "chat-1",
                userId: $0.id,
                lastReadAt: isoNow()
            )
        }
        mock.participantsByConversation = ["chat-1": participants, "chat-2": Array(participants.prefix(2))]
        mock.reactionsByConversation = [
            "chat-1": [MessageReactionModel(
                id: "r1",
                messageId: "m1",
                conversationId: "chat-1",
                userId: nora.id,
                emoji: "🌮"
            )],
        ]
        mock.insertConversationResult = group
    }

    private static func fillLists(_ mock: MockRepository) {
        let list = ShoppingListModel(
            id: "l1",
            title: "Groceries",
            ownerUserId: emma.id,
            familyId: familyId,
            color: 0xFF14B8A6
        )
        let items = [
            ShoppingItemModel(id: "i1", listId: "l1", item: "Oat milk"),
            ShoppingItemModel(id: "i2", listId: "l1", item: "Taco shells"),
            ShoppingItemModel(id: "i3", listId: "l1", item: "Cheddar", checked: true),
        ]
        mock.shoppingListsForUserResult = [list]
        mock.shoppingListDetailResult = [list]
        mock.shoppingItemsForIdsResult = items
        mock.shoppingItemsForListResult = items
        mock.shoppingListsResult = [list]
        mock.uncheckedItemsResult = Array(items.prefix(2))

        let wishlist = WishlistModel(id: "w1", ownerUserId: nora.id, familyId: familyId, name: "Nora's birthday")
        mock.wishlistsForUserResult = [wishlist]
        mock.wishlistDetailResult = [wishlist]
        mock.sharedWishlistsResult = []
        mock.wishesForListResult = [
            WishModel(
                id: "wish-1",
                wishlistId: "w1",
                userId: nora.id,
                text: "Football boots",
                price: "699 kr",
                description: "Size 36"
            ),
            WishModel(
                id: "wish-2",
                wishlistId: "w1",
                userId: nora.id,
                text: "Drawing tablet",
                link: "https://example.com/tablet"
            ),
            WishModel(id: "wish-3", wishlistId: "w1", userId: nora.id, text: "Board game", checked: true),
        ]
        mock.wishReservationsResult = [WishReservationModel(id: "res-1", wishId: "wish-1", reservedBy: emma.id)]
    }

    private static func fillPlanning(_ mock: MockRepository) {
        let today = LocalDate.today()
        let plan = MealPlanModel(
            id: "plan-1",
            familyId: familyId,
            fromDate: today.description,
            toDate: today.description,
            week: 40,
            name: "This week",
            createdBy: emma.id
        )
        let days = [
            MealPlanDayModel(id: "d1", mealPlanId: "plan-1", day: "Monday", date: today.description, food: "Tacos"),
            MealPlanDayModel(id: "d2", mealPlanId: "plan-1", day: "Tuesday", date: today.description, food: ""),
        ]
        mock.mealPlansResult = [plan]
        mock.mealPlanDetailResult = [plan]
        mock.mealPlanDaysResult = days
        mock.mealPlanDaysForPlanResult = days
        mock.mealPlanDaysForIdsResult = days
        mock.insertMealPlanResult = plan

        let events = [
            CalendarEventModel(
                id: "e1",
                userId: nora.id,
                familyId: familyId,
                dateFrom: today.description,
                dateTo: today.description,
                timeFrom: "17:30",
                timeTo: "19:00",
                activity: "Football practice",
                attendeeIds: [nora.id, lars.id]
            ),
            CalendarEventModel(
                id: "e2",
                userId: emma.id,
                familyId: familyId,
                dateFrom: today.description,
                dateTo: today.description,
                activity: "Cabin weekend",
                allDay: true,
                icon: "home",
                color: 0xFF6366F1
            ),
            CalendarEventModel(
                id: "e3",
                userId: emma.id,
                dateFrom: today.description,
                dateTo: today.description,
                timeFrom: "08:00",
                timeTo: "09:00",
                activity: "Dentist",
                isPrivate: true
            ),
        ]
        mock.calendarEventsResult = events
        mock.homeEventsResult = events
        let birthdays = [
            BirthdayModel(
                id: "b1",
                name: "Nora Nordmann",
                date: nora.birthday,
                familyId: familyId,
                userId: nora.id,
                madeByUserId: emma.id
            ),
            BirthdayModel(
                id: "b2",
                name: "Grandma Ingrid",
                date: today.description,
                familyId: familyId,
                madeByUserId: emma.id,
                icon: "celebration",
                color: 0xFFF59E0B
            ),
        ]
        mock.birthdaysResult = birthdays
        mock.homeBirthdaysResult = birthdays
    }
}

@MainActor
enum ScreenRenderer {
    /// Hosts `view` in a tall window, lets loads run, and lays it out again so the body
    /// reflects loaded state. Renders once in light and once in dark mode.
    static func render(_ view: some View, width: CGFloat = 430, height: CGFloat = 2600) async {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let host = UIHostingController(rootView: AnyView(view.environment(\.locale, Locale(identifier: "en"))))
            host.overrideUserInterfaceStyle = style
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: width, height: height))
            window.rootViewController = host
            window.makeKeyAndVisible()
            for _ in 0..<6 {
                host.view.setNeedsLayout()
                host.view.layoutIfNeeded()
                try? await Task.sleep(nanoseconds: 30_000_000)
            }
            XCTAssertNotNil(host.view.window)
            window.isHidden = true
        }
    }

    /// Lets a view model's init-time load finish against the mock.
    static func settle() async {
        try? await Task.sleep(nanoseconds: 200_000_000)
    }
}
