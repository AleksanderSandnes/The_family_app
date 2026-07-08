// Behaviour tests for HomeViewModel via MockRepository (no live backend). Covers dashboard
// summary assembly and the 7-day birthday gate.
@testable import FamilyApp
import XCTest

@MainActor
final class HomeViewModelTests: XCTestCase {
    private func makeMock(
        userId: String = "u1",
        familyId: String? = "f1",
        memberCount: Int = 2
    ) -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.name = "Alice"
        user.familyId = familyId
        mock.users[userId] = user
        if let familyId {
            var family = FamilyModel()
            family.id = familyId
            family.name = "The Family"
            mock.families[familyId] = family
            mock.familyMembers = (0..<memberCount).map { idx in
                var member = UserModel()
                member.id = "m\(idx)"
                member.familyId = familyId
                return member
            }
        }
        return mock
    }

    /// A birthday ISO string whose next occurrence is `offset` days from today.
    private func birthdayDate(daysFromToday offset: Int, year: Int = 1990) -> String {
        let target = LocalDate.today().addingDays(offset)
        return LocalDate(year: year, month: target.month, day: target.day).isoString
    }

    private func makeVM(_ mock: MockRepository) -> HomeViewModel {
        HomeViewModel(repo: mock)
    }

    func testDashboardSummaryAssemblesFromCannedData() async {
        let mock = makeMock(memberCount: 3)
        let today = LocalDate.today()

        var plan = MealPlanModel()
        plan.id = "p1"
        plan.fromDate = today.addingDays(-1).isoString
        plan.toDate = today.addingDays(1).isoString
        mock.mealPlansResult = [plan]
        var day = MealPlanDayModel()
        day.food = "Tacos"
        mock.mealPlanDaysResult = [day]

        var event = CalendarEventModel()
        event.id = "e1"
        event.activity = "Dentist"
        event.familyId = "f1"
        event.dateFrom = today.addingDays(2).isoString
        event.dateTo = today.addingDays(2).isoString
        mock.homeEventsResult = [event]

        var birthday = BirthdayModel()
        birthday.id = "b1"
        birthday.name = "Grandma"
        birthday.familyId = "f1"
        birthday.date = birthdayDate(daysFromToday: 3)
        mock.homeBirthdaysResult = [birthday]

        var list = ShoppingListModel()
        list.id = "l1"
        mock.shoppingListsResult = [list]
        mock.uncheckedItemsResult = (0..<4).map { idx in
            var item = ShoppingItemModel()
            item.id = "i\(idx)"
            item.listId = "l1"
            return item
        }

        let vm = makeVM(mock)
        await waitUntil { vm.state.tonightMeal == "Tacos" }

        XCTAssertEqual(vm.state.memberCount, 3)
        XCTAssertEqual(vm.state.family?.name, "The Family")
        XCTAssertEqual(vm.state.tonightMeal, "Tacos")
        XCTAssertEqual(vm.state.nextEventTitle, "Dentist")
        XCTAssertNotNil(vm.state.nextEventWhen)
        XCTAssertEqual(vm.state.nextBirthdayName, "Grandma")
        XCTAssertNotNil(vm.state.nextBirthdayWhen)
        XCTAssertEqual(vm.state.shoppingRemaining, 4)
        XCTAssertTrue(vm.state.hasSummary)
        XCTAssertFalse(vm.state.isLoading)
    }

    func testBirthdayWithinSevenDaysIsSurfaced() async {
        let mock = makeMock()
        var birthday = BirthdayModel()
        birthday.id = "b1"
        birthday.name = "Bob"
        birthday.familyId = "f1"
        birthday.date = birthdayDate(daysFromToday: 5)
        mock.homeBirthdaysResult = [birthday]

        let vm = makeVM(mock)
        await waitUntil { vm.state.nextBirthdayName == "Bob" }
        XCTAssertEqual(vm.state.nextBirthdayName, "Bob")
    }

    func testBirthdayBeyondSevenDaysIsNotSurfaced() async {
        let mock = makeMock()
        var birthday = BirthdayModel()
        birthday.id = "b1"
        birthday.name = "Faraway"
        birthday.familyId = "f1"
        birthday.date = birthdayDate(daysFromToday: 10)
        mock.homeBirthdaysResult = [birthday]

        let vm = makeVM(mock)
        // The whole state is assigned in one shot at the end of load(); waiting on the
        // loaded user guarantees the summary (and its birthday gate) has been computed.
        await waitUntil { !vm.state.isLoading && vm.state.user != nil }
        XCTAssertNil(vm.state.nextBirthdayName)
        XCTAssertNil(vm.state.nextBirthdayWhen)
    }

    func testBirthdayExactlySevenDaysIsSurfaced() async {
        let mock = makeMock()
        var birthday = BirthdayModel()
        birthday.id = "b1"
        birthday.name = "Edge"
        birthday.familyId = "f1"
        birthday.date = birthdayDate(daysFromToday: 7)
        mock.homeBirthdaysResult = [birthday]

        let vm = makeVM(mock)
        await waitUntil { vm.state.nextBirthdayName == "Edge" }
        XCTAssertEqual(vm.state.nextBirthdayName, "Edge")
    }

    func testNoFamilyProducesEmptySummary() async {
        let mock = makeMock(familyId: nil)
        let vm = makeVM(mock)
        await waitUntil { !vm.state.isLoading && vm.state.user != nil }
        XCTAssertNil(vm.state.tonightMeal)
        XCTAssertNil(vm.state.nextEventTitle)
        XCTAssertNil(vm.state.nextBirthdayName)
        XCTAssertEqual(vm.state.shoppingRemaining, 0)
        XCTAssertFalse(vm.state.hasSummary)
    }

    func testSignedOutClearsState() async {
        let mock = MockRepository() // no signIn
        let vm = makeVM(mock)
        await waitUntil { !vm.state.isLoading }
        XCTAssertNil(vm.state.user)
        XCTAssertFalse(vm.state.hasSummary)
    }
}
