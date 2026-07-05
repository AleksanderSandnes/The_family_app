// Minimal java.time.LocalDate equivalent — a plain calendar date with no timezone,
// so date math matches the Android implementation exactly (ISO yyyy-MM-dd strings
// in the DB are timezone-less).
import Foundation

struct LocalDate: Comparable, Hashable, CustomStringConvertible {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Parses the leading `yyyy-MM-dd` of an ISO string; nil when malformed.
    init?(iso: String) {
        let trimmed = iso.trimmingCharacters(in: .whitespaces)
        let pattern = #"^(\d{4})-(\d{2})-(\d{2})"#
        guard let match = trimmed.range(of: pattern, options: .regularExpression) else {
            return nil
        }
        let parts = trimmed[match].split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3,
              (1...12).contains(parts[1]),
              (1...31).contains(parts[2]),
              parts[2] <= LocalDate.daysIn(month: parts[1], year: parts[0])
        else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    static func today(calendar: Calendar = .current) -> LocalDate {
        let parts = calendar.dateComponents([.year, .month, .day], from: Date())
        guard let year = parts.year, let month = parts.month, let day = parts.day else {
            preconditionFailure("Calendar returned incomplete date components for the current date")
        }
        return LocalDate(year: year, month: month, day: day)
    }

    var isoString: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    var description: String {
        isoString
    }

    /// Days since 1970-01-01 (Howard Hinnant's days_from_civil).
    var epochDay: Int {
        var y = year
        if month <= 2 { y -= 1 }
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /// Inverse of epochDay (civil_from_days).
    init(epochDay: Int) {
        let z = epochDay + 719468
        let era = (z >= 0 ? z : z - 146096) / 146097
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let y = yoe + era * 400
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let day = doy - (153 * mp + 2) / 5 + 1
        let month = mp < 10 ? mp + 3 : mp - 9
        self.init(year: month <= 2 ? y + 1 : y, month: month, day: day)
    }

    /// Same date in another year; clamps Feb 29 → Feb 28 like java.time's withYear.
    func withYear(_ newYear: Int) -> LocalDate {
        let clampedDay = min(day, LocalDate.daysIn(month: month, year: newYear))
        return LocalDate(year: newYear, month: month, day: clampedDay)
    }

    func addingDays(_ days: Int) -> LocalDate {
        LocalDate(epochDay: epochDay + days)
    }

    func daysUntil(_ other: LocalDate) -> Int {
        other.epochDay - epochDay
    }

    /// English formatting like Android's "EEE d MMM" (e.g. "Mon 6 Jul").
    func formattedShort() -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochDay) * 86400)
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE d MMM"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: date)
    }

    static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    static func daysIn(month: Int, year: Int) -> Int {
        switch month {
        case 1, 3, 5, 7, 8, 10, 12: 31
        case 4, 6, 9, 11: 30
        case 2: isLeap(year) ? 29 : 28
        default: 0
        }
    }

    static func isLeap(_ year: Int) -> Bool {
        (year.isMultiple(of: 4) && !year.isMultiple(of: 100)) || year.isMultiple(of: 400)
    }
}
