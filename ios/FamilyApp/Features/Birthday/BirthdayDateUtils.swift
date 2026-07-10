// Birthday date math.
import Foundation

/// Next occurrence of an ISO birthday on or after `today`; nil when malformed.
func nextBirthdayDate(_ isoDate: String, today: LocalDate = .today()) -> LocalDate? {
    guard let birthday = LocalDate(iso: isoDate) else { return nil }
    let thisYear = birthday.withYear(today.year)
    return thisYear < today ? birthday.withYear(today.year + 1) : thisYear
}

/// The age the person turns on their next birthday; nil when malformed.
func turnsAge(_ isoDate: String, today: LocalDate = .today()) -> Int? {
    guard let birthday = LocalDate(iso: isoDate),
          let next = nextBirthdayDate(isoDate, today: today) else { return nil }
    return next.year - birthday.year
}
