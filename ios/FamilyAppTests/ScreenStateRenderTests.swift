@testable import FamilyApp

// Render tests for alternate screen states and the sheets/rows that only appear after a
// user interaction: owner wish rows, the no-family home banner, map pins from fictional
// locations, empty states, and each creation/detail sheet rendered directly.
import SwiftUI
import XCTest

@MainActor
final class ScreenStateRenderTests: XCTestCase {
    override func setUp() async throws {
        try await super.setUp()
        StubSupabase.install()
    }

    override func tearDown() async throws {
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    // MARK: - Data-driven states

    func testOwnedWishlistShowsOwnerRows() async {
        let mock = DemoFamily.mock()
        mock.wishlistDetailResult = mock.wishlistDetailResult.map { list in
            var owned = list
            owned.ownerUserId = DemoFamily.emma.id
            return owned
        }
        mock.wishesForListResult = mock.wishesForListResult.map { wish in
            var mine = wish
            mine.userId = DemoFamily.emma.id
            return mine
        }
        let vm = WishlistViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { WishlistDetailScreen(wishlistId: "w1", viewModel: vm) })
    }

    func testHomeWithoutAFamilyShowsTheBanner() async {
        let mock = DemoFamily.mock()
        var solo = DemoFamily.emma
        solo.familyId = nil
        mock.users[solo.id] = solo
        let vm = HomeViewModel(repo: mock)
        await ScreenRenderer.settle()
        await ScreenRenderer.render(
            NavigationStack {
                HomeScreen(viewModel: vm, onOpen: { _ in }, onOpenCalendarTab: {}, onOpenFamily: {})
            }
        )
    }

    func testEmptyFeatureScreens() async {
        let mock = MockRepository()
        mock.session.signIn(userId: "solo")
        var solo = UserModel()
        solo.id = "solo"
        solo.name = "Solo Tester"
        mock.users[solo.id] = solo
        let shopping = ShoppingViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let meals = MealViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let wishes = WishlistViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let calendar = CalendarViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let birthdays = BirthdayViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        let chat = ChatViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
        await ScreenRenderer.settle()
        await ScreenRenderer.render(NavigationStack { ShoppingScreen(viewModel: shopping, onOpenList: { _ in }) })
        await ScreenRenderer.render(NavigationStack { MealScreen(viewModel: meals, onOpen: { _ in }) })
        await ScreenRenderer.render(NavigationStack { WishlistScreen(viewModel: wishes, onOpen: { _ in }) })
        await ScreenRenderer.render(NavigationStack { CalendarScreen(viewModel: calendar) })
        await ScreenRenderer.render(NavigationStack { BirthdayScreen(viewModel: birthdays) })
        await ScreenRenderer.render(NavigationStack { ChatScreen(viewModel: chat, onOpen: { _ in }) })
    }

    func testMapShowsFictionalMemberPins() async {
        SessionStore.shared.signIn(userId: DemoFamily.emma.id)
        StubTransport.respond(table: "users", with: [DemoFamily.emma])
        StubTransport.respond(
            table: "user_locations",
            with: [
                UserLocationModel(
                    userId: DemoFamily.lars.id,
                    familyId: DemoFamily.familyId,
                    lat: 59.9139,
                    lng: 10.7522,
                    displayName: "Lars Nordmann",
                    visible: true,
                    updatedAt: isoNow()
                ),
                UserLocationModel(
                    userId: DemoFamily.nora.id,
                    familyId: DemoFamily.familyId,
                    lat: 59.9270,
                    lng: 10.7008,
                    displayName: "Nora Nordmann",
                    visible: true,
                    updatedAt: isoNow()
                ),
            ]
        )
        await ScreenRenderer.render(NavigationStack { FamilyMapScreen() })
    }

    // MARK: - Sheets and rows

    func testWishAndListSheets() async {
        let wish = DemoFamily.mock().wishesForListResult[0]
        for state in [WishReservationState.available, .reservedByMe, .reservedByOther] {
            await ScreenRenderer.render(WishDetailSheet(wish: wish, state: state, onReserve: {}, onUnreserve: {}))
        }
        await ScreenRenderer.render(NewListSheet(onCreate: { _, _, _ in }))
    }

    func testCalendarSubviews() async {
        let events = DemoFamily.mock().calendarEventsResult
        let today = LocalDate.today()
        await ScreenRenderer.render(AgendaList(events: events, onEdit: { _ in }, onDelete: { _ in }))
        await ScreenRenderer.render(AgendaList(events: [], onEdit: { _ in }, onDelete: { _ in }))
        await ScreenRenderer.render(
            WeekStrip(selectedDate: today, dotColorsByDate: [today: [.purple, .teal]], onDaySelected: { _ in })
        )
        await ScreenRenderer.render(
            AttendeePickerSheet(
                members: [DemoFamily.emma, DemoFamily.lars, DemoFamily.nora],
                selection: .constant([DemoFamily.lars.id])
            )
        )
    }

    func testMealSubviews() async {
        let day = DemoFamily.mock().mealPlanDaysResult[0]
        await ScreenRenderer.render(CreatePlanSheet(onCreate: { _, _, _, _, _ in }))
        await ScreenRenderer.render(PlanDatePicker(label: "From", selection: .constant(nil)))
        await ScreenRenderer.render(PlanDatePicker(label: "To", selection: .constant(Date())))
        for editing in [false, true] {
            await ScreenRenderer.render(
                MealDayRow(
                    day: day,
                    isEditing: editing,
                    draft: .constant("Tacos"),
                    onStartEdit: {},
                    onSave: {},
                    onCancel: {}
                )
            )
        }
        var empty = day
        empty.food = ""
        await ScreenRenderer.render(
            MealDayRow(day: empty, isEditing: false, draft: .constant(""), onStartEdit: {}, onSave: {}, onCancel: {})
        )
    }

    func testFamilyAndBirthdaySheets() async {
        await ScreenRenderer.render(CreateFamilySheet(onCreate: { _, _ in }))
        await ScreenRenderer.render(
            QrSheet(family: FamilyModel(id: DemoFamily.familyId, name: "Family Nordmann", joinCode: "DEMO42"))
        )
        await ScreenRenderer.render(BirthdaySheet(
            title: "New birthday",
            confirmLabel: "Add",
            onConfirm: { _, _, _, _ in }
        ))
        await ScreenRenderer.render(
            BirthdaySheet(
                title: "Edit birthday",
                confirmLabel: "Save",
                initialName: "Grandma Ingrid",
                initialDate: "1952-10-13",
                initialIcon: "celebration",
                initialColor: nil,
                onConfirm: { _, _, _, _ in }
            )
        )
    }
}
