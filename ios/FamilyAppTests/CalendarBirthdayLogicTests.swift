@testable import FamilyApp

// Calendar + birthday pure-logic tests — mirror the grid/label rules in
// CalendarScreen.kt and BirthdayScreen.kt.
import XCTest

final class YearMonthTests: XCTestCase {
    func testPlusMonthsRollsOverYears() {
        XCTAssertEqual(YearMonth(year: 2026, month: 12).plusMonths(1), YearMonth(year: 2027, month: 1))
        XCTAssertEqual(YearMonth(year: 2026, month: 1).plusMonths(-1), YearMonth(year: 2025, month: 12))
        XCTAssertEqual(YearMonth(year: 2026, month: 7).plusMonths(18), YearMonth(year: 2028, month: 1))
    }

    func testFormatted() {
        XCTAssertEqual(YearMonth(year: 2026, month: 7).formatted(), "July 2026")
    }

    func testComparable() {
        XCTAssertLessThan(YearMonth(year: 2026, month: 6), YearMonth(year: 2026, month: 7))
        XCTAssertLessThan(YearMonth(year: 2025, month: 12), YearMonth(year: 2026, month: 1))
    }
}

final class MonthCellsTests: XCTestCase {
    func testJuly2026StartsWednesday() {
        // 2026-07-01 is a Wednesday → 2 leading nils (Monday-first).
        let cells = monthCells(YearMonth(year: 2026, month: 7))
        XCTAssertNil(cells[0])
        XCTAssertNil(cells[1])
        XCTAssertEqual(cells[2], LocalDate(iso: "2026-07-01"))
        XCTAssertEqual(cells.count % 7, 0)
        XCTAssertEqual(cells.compactMap { $0 }.count, 31)
    }

    func testMondayStartHasNoLeadingNils() {
        // 2026-06-01 is a Monday.
        let cells = monthCells(YearMonth(year: 2026, month: 6))
        XCTAssertEqual(cells[0], LocalDate(iso: "2026-06-01"))
        XCTAssertEqual(cells.compactMap { $0 }.count, 30)
    }

    func testFebruaryLeapYear() {
        let cells = monthCells(YearMonth(year: 2024, month: 2))
        XCTAssertEqual(cells.compactMap { $0 }.count, 29)
        XCTAssertEqual(cells.count % 7, 0)
    }
}

final class CalendarHelpersTests: XCTestCase {
    private func event(
        from: String, to: String = "", icon: String = "schedule",
        allDay: Bool = false, timeFrom: String = "", timeTo: String = ""
    ) -> CalendarEventModel {
        var event = CalendarEventModel()
        event.dateFrom = from
        event.dateTo = to
        event.icon = icon
        event.allDay = allDay
        event.timeFrom = timeFrom
        event.timeTo = timeTo
        return event
    }

    func testDateEventColorsSingleDay() {
        let map = dateEventColors(for: [event(from: "2026-07-05", icon: "cake")])
        XCTAssertEqual(map[LocalDate(iso: "2026-07-05")!]?.count, 1)
        XCTAssertNil(map[LocalDate(iso: "2026-07-06")!])
    }

    func testDateEventColorsMultiDaySpansRange() {
        let map = dateEventColors(for: [event(from: "2026-07-05", to: "2026-07-07")])
        XCTAssertEqual(map[LocalDate(iso: "2026-07-05")!]?.count, 1)
        XCTAssertEqual(map[LocalDate(iso: "2026-07-06")!]?.count, 1)
        XCTAssertEqual(map[LocalDate(iso: "2026-07-07")!]?.count, 1)
        XCTAssertNil(map[LocalDate(iso: "2026-07-08")!])
    }

    func testDateEventColorsCapsAt60Days() {
        let map = dateEventColors(for: [event(from: "2026-01-01", to: "2026-12-31")])
        XCTAssertNotNil(map[LocalDate(iso: "2026-01-01")!])
        XCTAssertNil(map[LocalDate(iso: "2026-06-01")!])
    }

    func testDateEventColorsSkipsMalformed() {
        XCTAssertTrue(dateEventColors(for: [event(from: "not-a-date")]).isEmpty)
    }

    func testEventTimeLabel() {
        XCTAssertEqual(eventTimeLabel(event(from: "x", allDay: true, timeFrom: "09:00")), "All day")
        XCTAssertEqual(eventTimeLabel(event(from: "x", timeFrom: "09:00", timeTo: "10:30")), "09:00 – 10:30")
        XCTAssertEqual(eventTimeLabel(event(from: "x", timeFrom: "09:00")), "09:00")
        XCTAssertEqual(eventTimeLabel(event(from: "x")), "")
    }

    func testSectionDateLabel() {
        XCTAssertEqual(sectionDateLabel(LocalDate(iso: "2026-07-05")!), "Sunday, 5 July")
    }

    func testIconColorIndexFallsBackToZero() {
        XCTAssertEqual(calendarIconColorIndex("schedule"), 0)
        XCTAssertEqual(calendarIconColorIndex("restaurant"), 5)
        XCTAssertEqual(calendarIconColorIndex("unknown"), 0)
    }

    func testCalendarIconOptionsAllMapped() {
        XCTAssertEqual(IconOptions.calendar.count, 16)
        for key in IconOptions.calendar {
            XCTAssertNotEqual(IconKeyMap.symbol(key, fallback: "MISSING"), "MISSING", key)
        }
    }
}

final class BirthdaySortTests: XCTestCase {
    private func birthday(_ name: String, _ date: String) -> BirthdayModel {
        var birthday = BirthdayModel()
        birthday.name = name
        birthday.date = date
        return birthday
    }

    func testSortsByNextOccurrenceNotCalendarDate() {
        let today = LocalDate(iso: "2026-07-05")!
        let sorted = sortedByNextBirthday(
            [
                birthday("January", "1990-01-15"), // next: 2027-01-15
                birthday("December", "1985-12-24"), // next: 2026-12-24
                birthday("Tomorrow", "2000-07-06"), // next: 2026-07-06
            ],
            today: today
        )
        XCTAssertEqual(sorted.map(\.name), ["Tomorrow", "December", "January"])
    }

    func testMalformedDatesSortLast() {
        let today = LocalDate(iso: "2026-07-05")!
        let sorted = sortedByNextBirthday(
            [birthday("Broken", "garbage"), birthday("Valid", "1990-08-01")],
            today: today
        )
        XCTAssertEqual(sorted.map(\.name), ["Valid", "Broken"])
    }
}
