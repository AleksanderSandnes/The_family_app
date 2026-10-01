@testable import FamilyApp

// Render tests: every top-level screen and sheet is hosted in a real window with the
// fictional Nordmann family loaded, in light and dark mode. They catch crashes in view
// bodies and keep SwiftUI layout code exercised; behaviour lives in the view-model tests.
import SwiftUI
import XCTest

@MainActor
final class ScreenRenderTests: XCTestCase {
    override func setUp() async throws {
        try await super.setUp()
        // Screens that build their own view models use FamilyRepository.shared; keep them
        // on the stub transport so nothing reaches a network.
        StubSupabase.install()
    }

    override func tearDown() async throws {
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    // MARK: - Feature screens

    func testHomeScreen() async {
        let vm = HomeViewModel(repo: DemoFamily.mock())
        await ScreenRenderer.settle()
        await ScreenRenderer.render(
            NavigationStack {
                HomeScreen(viewModel: vm, onOpen: { _ in }, onOpenCalendarTab: {}, onOpenFamily: {})
            }
        )
    }

    func testShoppingScreens() async {
        let vm = ShoppingViewModel(repo: DemoFamily.mock(), realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { ShoppingScreen(viewModel: vm, onOpenList: { _ in }) })
        await ScreenRenderer.render(NavigationStack { ShoppingDetailScreen(listId: "l1", viewModel: vm) })
    }

    func testMealScreens() async {
        let vm = MealViewModel(repo: DemoFamily.mock(), realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { MealScreen(viewModel: vm, onOpen: { _ in }) })
        await ScreenRenderer.render(NavigationStack { MealDetailScreen(planId: "plan-1", viewModel: vm) })
    }

    func testWishlistScreens() async {
        let vm = WishlistViewModel(repo: DemoFamily.mock(), realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { WishlistScreen(viewModel: vm, onOpen: { _ in }) })
        await ScreenRenderer.render(NavigationStack { WishlistDetailScreen(wishlistId: "w1", viewModel: vm) })
    }

    func testCalendarScreen() async {
        let vm = CalendarViewModel(repo: DemoFamily.mock(), realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { CalendarScreen(viewModel: vm) })
    }

    func testBirthdayScreen() async {
        let vm = BirthdayViewModel(repo: DemoFamily.mock(), realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { BirthdayScreen(viewModel: vm) })
    }

    func testChatScreens() async {
        let vm = ChatViewModel(repo: DemoFamily.mock(), realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { ChatScreen(viewModel: vm, onOpen: { _ in }) })
        await ScreenRenderer.render(NavigationStack { ConversationScreen(conversationId: "chat-1", viewModel: vm) })
        await ScreenRenderer.render(NavigationStack { ConversationScreen(conversationId: "chat-2", viewModel: vm) })
        await ScreenRenderer.render(NewConversationSheet(viewModel: vm, onCreate: { _, _ in }))
    }

    func testFamilyScreen() async {
        let vm = FamilyViewModel(repo: DemoFamily.mock())
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { FamilyScreen(viewModel: vm) })
    }

    func testFamilyScreenWithoutAFamily() async {
        let mock = DemoFamily.mock()
        var solo = DemoFamily.emma
        solo.familyId = nil
        mock.users[solo.id] = solo
        let vm = FamilyViewModel(repo: mock)
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { FamilyScreen(viewModel: vm) })
    }

    func testProfileScreens() async {
        let vm = ProfileViewModel(repo: DemoFamily.mock())
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { ProfileScreen(viewModel: vm, onEdit: {}, onSettings: {}) })
        await ScreenRenderer.render(NavigationStack { ProfileEditScreen(viewModel: vm) })
        await ScreenRenderer.render(ProfileCompletionSheet(onSave: { _, _ in }, onSkip: {}))
    }

    func testSelfContainedScreens() async {
        await ScreenRenderer.render(NavigationStack { SettingsScreen() })
        await ScreenRenderer.render(NavigationStack { FamilyMapScreen() })
        await ScreenRenderer.render(PermissionsOnboardingScreen(onComplete: {}))
        await ScreenRenderer.render(MainTabView().environment(DeepLinkRouter(repo: DemoFamily.mock())))
        await ScreenRenderer.render(PlaceholderScreen(title: "Soon"))
    }

    // MARK: - Auth

    func testAuthScreens() async {
        let auth = AuthViewModel(repo: DemoFamily.mock())
        await ScreenRenderer.render(AuthFlowView())
        await ScreenRenderer.render(
            NavigationStack { LoginScreen(viewModel: auth, onNavigateToRegister: {}, onNavigateToReset: {}) }
        )
        await ScreenRenderer.render(NavigationStack { RegisterScreen(viewModel: auth) })
        await ScreenRenderer.render(NavigationStack { ResetPasswordScreen(viewModel: auth) })
        await ScreenRenderer.render(NavigationStack { VerifyEmailScreen(viewModel: auth, sendCode: false) })
        await ScreenRenderer.render(NavigationStack { VerifyEmailScreen(viewModel: auth, sendCode: true) })
        for step in 0..<3 {
            await ScreenRenderer.render(StepIndicator(currentStep: step))
        }
        for password in ["", "abc", "Abcdef12", "Abcdef12!long-pass"] {
            await ScreenRenderer.render(PasswordStrengthBar(password: password))
        }
        await ScreenRenderer.render(AuthFooter(prompt: "No account?", action: "Register", onTap: {}))
        await ScreenRenderer.render(BirthdayPickerField(isoDate: .constant("2014-06-21")))
        await ScreenRenderer.render(BirthdayPickerField(isoDate: .constant("")))
    }

    // MARK: - Sheets and components

    func testCalendarAndFamilySheets() async {
        let members = [DemoFamily.emma, DemoFamily.lars, DemoFamily.nora]
        let event = DemoFamily.mock().calendarEventsResult[0]
        await ScreenRenderer.render(EventSheet(
            existingEvent: nil,
            initialDate: LocalDate.today(),
            members: members,
            onSave: { _ in }
        ))
        await ScreenRenderer.render(EventSheet(
            existingEvent: event,
            initialDate: LocalDate.today(),
            members: members,
            onSave: { _ in }
        ))
        await ScreenRenderer.render(EventColorPicker(selection: .constant(nil)))
        await ScreenRenderer.render(EventColorPicker(selection: .constant(0xFF6366F1)))
        await ScreenRenderer.render(
            MemberProfileSheet(member: DemoFamily.lars, isSelf: false, relation: "Husband", onSetRelation: { _ in })
        )
        await ScreenRenderer.render(MemberProfileSheet(
            member: DemoFamily.emma,
            isSelf: true,
            relation: "",
            onSetRelation: { _ in }
        ))
        await ScreenRenderer.render(RelationsSetupSheet(
            members: members,
            relations: ["lars": "Husband"],
            onSet: { _, _ in }
        ))
    }

    func testListSheets() async {
        await ScreenRenderer.render(
            IconPickerSheet(
                title: "Icon",
                options: ["shopping_cart", "kitchen", "cake"],
                selected: "kitchen",
                symbolFor: { _ in "cart" },
                onPick: { _ in },
                initialColor: 0xFF14B8A6,
                onColorPick: { _ in }
            )
        )
        await ScreenRenderer.render(
            IconPickerSheet(
                title: "Icon",
                options: ["cake"],
                selected: "cake",
                symbolFor: { _ in "gift" },
                onPick: { _ in },
                initialColor: nil,
                onColorPick: nil
            )
        )
        await ScreenRenderer.render(FloatingActionButton(text: "New list", action: {}))
        await ScreenRenderer.render(NewWishlistSheet(onCreate: { _, _, _ in }))
        await ScreenRenderer.render(AddWishSheet(initial: nil, onConfirm: { _ in }))
        await ScreenRenderer.render(AddWishSheet(
            initial: DemoFamily.mock().wishesForListResult[0],
            onConfirm: { _ in }
        ))
    }

    func testChatComponents() async {
        let mock = DemoFamily.mock()
        let messages = (mock.messagesByConversation["chat-1"] ?? []) + (mock.messagesByConversation["chat-2"] ?? [])
        for (index, message) in messages.enumerated() {
            await ScreenRenderer.render(
                MessageRow(
                    message: message,
                    isMine: index.isMultiple(of: 2),
                    isGroup: true,
                    sender: DemoFamily.lars,
                    quoted: index == 1 ? messages[0] : nil,
                    quotedSenderName: index == 1 ? "Lars" : nil,
                    reactions: index == 0 ? ["🌮": ["nora"]] : [:],
                    seen: index == 1,
                    isReactionTarget: index == 0,
                    onReact: { _ in },
                    onReply: {},
                    onOpenImage: { _ in },
                    onLongPress: {}
                )
            )
        }
        let vm = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(
            ModerationBar(message: messages[0], model: vm, picker: .constant("m1"), state: .constant(ModerationState()))
        )
        await ScreenRenderer.render(BlockMenuButton(model: vm, state: .constant(ModerationState())))
        await ScreenRenderer.render(ReactionBar(onPick: { _ in }))
        await ScreenRenderer.render(TypingIndicatorRow())
        await ScreenRenderer.render(MemberListSheet(title: "Members", members: [DemoFamily.lars], onPick: { _ in }))
        await ScreenRenderer.render(ImageViewer(url: "https://fictional.example/taco.jpg", onClose: {}))
        await ScreenRenderer.render(ConversationTitle(title: "Family Nordmann", presence: "Active now"))
        await ScreenRenderer.render(ConversationTitle(title: "Lars", presence: nil))
        await ScreenRenderer.render(MemberSelectRow(member: DemoFamily.nora, selected: true, onToggle: {}))
        await ScreenRenderer.render(VoiceNoteView(url: "https://fictional.example/v.m4a", tint: .purple))
        await ScreenRenderer.render(LiveWaveform(levels: [0.1, 0.5, 0.9, 0.3], tint: .purple))
        await ScreenRenderer.render(RecordingPulse())
    }
}
