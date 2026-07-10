@testable import FamilyApp

// Home/date pure-logic tests for date math, greeting, and event/birthday formatting.
import XCTest

final class LocalDateTests: XCTestCase {
    func testParsesIsoAndPrefixes() {
        XCTAssertEqual(LocalDate(iso: "2026-07-05"), LocalDate(year: 2026, month: 7, day: 5))
        // Timestamp prefix (like DB timestamptz strings) also parses.
        XCTAssertEqual(LocalDate(iso: "2026-07-05T10:00:00Z"), LocalDate(year: 2026, month: 7, day: 5))
    }

    func testRejectsMalformed() {
        XCTAssertNil(LocalDate(iso: ""))
        XCTAssertNil(LocalDate(iso: "24 Dec"))
        XCTAssertNil(LocalDate(iso: "2026-13-01"))
        XCTAssertNil(LocalDate(iso: "2026-02-30"))
    }

    func testComparison() {
        XCTAssertLessThan(LocalDate(iso: "2026-07-05")!, LocalDate(iso: "2026-07-06")!)
        XCTAssertLessThan(LocalDate(iso: "2026-06-30")!, LocalDate(iso: "2026-07-01")!)
        XCTAssertLessThan(LocalDate(iso: "2025-12-31")!, LocalDate(iso: "2026-01-01")!)
    }

    func testEpochDayRoundTrip() {
        // 1970-01-01 is epoch day 0.
        XCTAssertEqual(LocalDate(year: 1970, month: 1, day: 1).epochDay, 0)
        for iso in ["2026-07-05", "2000-02-29", "1999-12-31", "2024-03-01"] {
            let date = LocalDate(iso: iso)!
            XCTAssertEqual(LocalDate(epochDay: date.epochDay), date, iso)
        }
    }

    func testAddingDaysAndDaysUntil() {
        let date = LocalDate(iso: "2026-07-05")!
        XCTAssertEqual(date.addingDays(1), LocalDate(iso: "2026-07-06"))
        XCTAssertEqual(date.addingDays(27), LocalDate(iso: "2026-08-01"))
        XCTAssertEqual(date.daysUntil(LocalDate(iso: "2026-07-10")!), 5)
        XCTAssertEqual(date.daysUntil(LocalDate(iso: "2026-07-01")!), -4)
    }

    func testWithYearClampsLeapDay() {
        let leap = LocalDate(iso: "2024-02-29")!
        XCTAssertEqual(leap.withYear(2025), LocalDate(year: 2025, month: 2, day: 28))
        XCTAssertEqual(leap.withYear(2028), LocalDate(year: 2028, month: 2, day: 29))
    }

    func testFormattedShort() {
        // 2026-07-05 is a Sunday.
        XCTAssertEqual(LocalDate(iso: "2026-07-05")!.formattedShort(), "Sun 5 Jul")
    }
}

final class BirthdayDateUtilsTests: XCTestCase {
    private let today = LocalDate(iso: "2026-07-05")!

    func testNextBirthdayLaterThisYear() {
        XCTAssertEqual(nextBirthdayDate("1990-12-24", today: today), LocalDate(iso: "2026-12-24"))
    }

    func testNextBirthdayAlreadyPassedRollsToNextYear() {
        XCTAssertEqual(nextBirthdayDate("1990-01-15", today: today), LocalDate(iso: "2027-01-15"))
    }

    func testBirthdayTodayStaysToday() {
        XCTAssertEqual(nextBirthdayDate("1990-07-05", today: today), LocalDate(iso: "2026-07-05"))
    }

    func testMalformedReturnsNil() {
        XCTAssertNil(nextBirthdayDate("24 Dec", today: today))
        XCTAssertNil(turnsAge("garbage", today: today))
    }

    func testTurnsAge() {
        XCTAssertEqual(turnsAge("1990-12-24", today: today), 36)
        XCTAssertEqual(turnsAge("1990-01-15", today: today), 37) // next occurrence is 2027
        XCTAssertEqual(turnsAge("1990-07-05", today: today), 36) // today
    }
}

final class HomeFormattingTests: XCTestCase {
    private let today = LocalDate(iso: "2026-07-05")!

    func testGreetingBoundaries() {
        XCTAssertEqual(timeBasedGreeting(hour: 0), "Good morning")
        XCTAssertEqual(timeBasedGreeting(hour: 11), "Good morning")
        XCTAssertEqual(timeBasedGreeting(hour: 12), "Good afternoon")
        XCTAssertEqual(timeBasedGreeting(hour: 17), "Good afternoon")
        XCTAssertEqual(timeBasedGreeting(hour: 18), "Good evening")
        XCTAssertEqual(timeBasedGreeting(hour: 23), "Good evening")
    }

    private func event(from: String, allDay: Bool = false, timeFrom: String = "") -> CalendarEventModel {
        var event = CalendarEventModel()
        event.dateFrom = from
        event.allDay = allDay
        event.timeFrom = timeFrom
        return event
    }

    func testEventWhenToday() {
        XCTAssertEqual(eventWhen(event(from: "2026-07-05"), today: today), "Today")
    }

    func testEventWhenTomorrowWithTime() {
        XCTAssertEqual(
            eventWhen(event(from: "2026-07-06", timeFrom: "18:00"), today: today),
            "Tomorrow · 18:00"
        )
    }

    func testEventWhenLaterDate() {
        XCTAssertEqual(eventWhen(event(from: "2026-07-10"), today: today), "Fri 10 Jul")
    }

    func testEventWhenAllDayOmitsTime() {
        XCTAssertEqual(
            eventWhen(event(from: "2026-07-05", allDay: true, timeFrom: "09:00"), today: today),
            "Today"
        )
    }

    func testEventWhenMalformedFallsBackToRaw() {
        XCTAssertEqual(eventWhen(event(from: "sometime"), today: today), "sometime")
    }

    // MARK: - eventHasEnded (dashboard "next event" drop-off)

    private func timedEvent(
        from: String, to: String? = nil, timeTo: String = "", allDay: Bool = false
    ) -> CalendarEventModel {
        var event = CalendarEventModel()
        event.dateFrom = from
        event.dateTo = to ?? from
        event.timeTo = timeTo
        event.allDay = allDay
        return event
    }

    func testEventEndedEarlierTodayIsOver() {
        // Ends 10:00, now 12:00 -> over.
        let event = timedEvent(from: "2026-07-05", timeTo: "10:00")
        XCTAssertTrue(eventHasEnded(event, today: today, nowMinutes: 12 * 60))
    }

    func testEventOngoingOrLaterTodayIsNotOver() {
        let event = timedEvent(from: "2026-07-05", timeTo: "18:00")
        XCTAssertFalse(eventHasEnded(event, today: today, nowMinutes: 12 * 60))
    }

    func testEventEndingExactlyNowIsOver() {
        let event = timedEvent(from: "2026-07-05", timeTo: "12:00")
        XCTAssertTrue(eventHasEnded(event, today: today, nowMinutes: 12 * 60))
    }

    func testFutureDayEventIsNotOver() {
        let event = timedEvent(from: "2026-07-06", timeTo: "08:00")
        XCTAssertFalse(eventHasEnded(event, today: today, nowMinutes: 23 * 60 + 59))
    }

    func testPastDayEventIsOver() {
        let event = timedEvent(from: "2026-07-04", timeTo: "18:00")
        XCTAssertTrue(eventHasEnded(event, today: today, nowMinutes: 0))
    }

    func testAllDayTodayStaysUntilDayEnd() {
        let event = timedEvent(from: "2026-07-05", timeTo: "09:00", allDay: true)
        XCTAssertFalse(eventHasEnded(event, today: today, nowMinutes: 23 * 60))
    }

    func testTodayWithoutEndTimeStaysUntilDayEnd() {
        let event = timedEvent(from: "2026-07-05", timeTo: "")
        XCTAssertFalse(eventHasEnded(event, today: today, nowMinutes: 23 * 60))
    }

    func testMultiDayEventEndingTomorrowIsNotOver() {
        let event = timedEvent(from: "2026-07-04", to: "2026-07-06", timeTo: "08:00")
        XCTAssertFalse(eventHasEnded(event, today: today, nowMinutes: 23 * 60))
    }

    func testMinutesSinceMidnight() {
        XCTAssertEqual(minutesSinceMidnight("18:30"), 18 * 60 + 30)
        XCTAssertEqual(minutesSinceMidnight("00:00"), 0)
        XCTAssertNil(minutesSinceMidnight(""))
        XCTAssertNil(minutesSinceMidnight("24:00"))
        XCTAssertNil(minutesSinceMidnight("noon"))
    }

    func testBirthdayWhenTodayTomorrowAndDays() {
        var birthday = BirthdayModel()
        birthday.date = "1990-07-05"
        XCTAssertEqual(
            birthdayWhen(birthday, next: LocalDate(iso: "2026-07-05")!, today: today),
            "Turns 36 · Today!"
        )
        birthday.date = "1990-07-06"
        XCTAssertEqual(
            birthdayWhen(birthday, next: LocalDate(iso: "2026-07-06")!, today: today),
            "Turns 36 · Tomorrow"
        )
        birthday.date = "1990-07-15"
        XCTAssertEqual(
            birthdayWhen(birthday, next: LocalDate(iso: "2026-07-15")!, today: today),
            "Turns 36 · in 10 days"
        )
    }

    func testBirthdayWhenWithoutParsableDateOmitsAge() {
        var birthday = BirthdayModel()
        birthday.date = "not-a-date"
        XCTAssertEqual(
            birthdayWhen(birthday, next: LocalDate(iso: "2026-07-06")!, today: today),
            "Tomorrow"
        )
    }
}
