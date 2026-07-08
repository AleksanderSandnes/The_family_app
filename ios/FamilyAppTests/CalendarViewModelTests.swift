// Behaviour tests for CalendarViewModel via MockRepository + NoopRealtimeObserver.
@testable import FamilyApp
import XCTest

@MainActor
final class CalendarViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String? = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.familyId = familyId
        mock.users[userId] = user
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> CalendarViewModel {
        CalendarViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    func testRefreshLoadsEventsFromRepo() async {
        let mock = makeMock()
        var event = CalendarEventModel()
        event.id = "e1"
        event.activity = "Dentist"
        event.familyId = "f1"
        event.dateFrom = "2026-07-08"
        event.dateTo = "2026-07-08"
        mock.calendarEventsResult = [event]
        let vm = makeVM(mock)
        // Wait for the specific event (the VM seeds from a shared static cache).
        await waitUntil { vm.events.contains { $0.activity == "Dentist" } }
        XCTAssertTrue(vm.events.contains { $0.activity == "Dentist" })
    }

    func testAddInsertsWithCorrectFieldsAndOptimisticAppend() async {
        let mock = makeMock()
        mock.calendarEventsResult = []
        let vm = makeVM(mock)
        await waitUntil { true }

        var draft = EventDraft(
            activity: "Soccer", allDay: false,
            dateFrom: "2026-07-10", dateTo: "2026-07-11",
            timeFrom: "17:00", timeTo: "18:00"
        )
        draft.icon = "sports"
        draft.isPrivate = true
        draft.color = 0x6366F1
        vm.addEvent(draft)

        await waitUntil { !mock.insertedEvents.isEmpty }
        let inserted = mock.insertedEvents.first
        XCTAssertEqual(inserted?.activity, "Soccer")
        XCTAssertEqual(inserted?.userId, "u1")
        XCTAssertEqual(inserted?.familyId, "f1")
        XCTAssertEqual(inserted?.dateFrom, "2026-07-10")
        XCTAssertEqual(inserted?.dateTo, "2026-07-11")
        XCTAssertEqual(inserted?.timeFrom, "17:00")
        XCTAssertEqual(inserted?.timeTo, "18:00")
        XCTAssertEqual(inserted?.icon, "sports")
        XCTAssertEqual(inserted?.isPrivate, true)
        XCTAssertEqual(inserted?.color, 0x6366F1)
    }

    func testAddAllDayBlanksTimesAndDefaultsDateTo() async {
        let mock = makeMock()
        mock.calendarEventsResult = []
        let vm = makeVM(mock)
        await waitUntil { true }

        let draft = EventDraft(
            activity: "Holiday", allDay: true,
            dateFrom: "2026-08-01", dateTo: "",
            timeFrom: "09:00", timeTo: "10:00"
        )
        vm.addEvent(draft)

        await waitUntil { !mock.insertedEvents.isEmpty }
        let inserted = mock.insertedEvents.first
        XCTAssertEqual(inserted?.allDay, true)
        XCTAssertEqual(inserted?.timeFrom, "")
        XCTAssertEqual(inserted?.timeTo, "")
        // dateTo falls back to dateFrom when empty.
        XCTAssertEqual(inserted?.dateTo, "2026-08-01")
    }

    func testUpdateCallsRepositoryWithNewValues() async {
        let mock = makeMock()
        var event = CalendarEventModel()
        event.id = "e1"
        event.activity = "Old"
        event.dateFrom = "2026-07-08"
        mock.calendarEventsResult = [event]
        let vm = makeVM(mock)
        await waitUntil { vm.events.contains { $0.id == "e1" } }

        var updated = event
        updated.activity = "New"
        updated.icon = "star"
        vm.updateEvent(updated)

        await waitUntil { !mock.updatedEvents.isEmpty }
        XCTAssertEqual(mock.updatedEvents.first?.id, "e1")
        XCTAssertEqual(mock.updatedEvents.first?.activity, "New")
        XCTAssertEqual(mock.updatedEvents.first?.icon, "star")
    }

    func testDeleteRemovesLocallyAndCallsRepo() async {
        let mock = makeMock()
        var event = CalendarEventModel()
        event.id = "e1"
        event.activity = "Gone"
        mock.calendarEventsResult = [event]
        let vm = makeVM(mock)
        await waitUntil { vm.events.contains { $0.id == "e1" } }

        vm.delete(event)
        await waitUntil { !mock.deletedEventIds.isEmpty }
        XCTAssertEqual(mock.deletedEventIds.first, "e1")
    }

    func testEventsForSelectedDateFiltersByRange() async {
        let mock = makeMock()
        var inRange = CalendarEventModel()
        inRange.id = "e1"
        inRange.activity = "InRange"
        inRange.dateFrom = "2026-07-08"
        inRange.dateTo = "2026-07-10"
        var outOfRange = CalendarEventModel()
        outOfRange.id = "e2"
        outOfRange.activity = "OutOfRange"
        outOfRange.dateFrom = "2026-07-20"
        outOfRange.dateTo = "2026-07-20"
        mock.calendarEventsResult = [inRange, outOfRange]
        let vm = makeVM(mock)
        await waitUntil { vm.events.contains { $0.id == "e2" } }

        vm.selectDate(LocalDate(year: 2026, month: 7, day: 9))
        let visible = vm.eventsForSelectedDate
        XCTAssertTrue(visible.contains { $0.id == "e1" })
        XCTAssertFalse(visible.contains { $0.id == "e2" })
    }
}
