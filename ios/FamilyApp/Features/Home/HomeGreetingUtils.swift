// Time-of-day greeting.
import Foundation

private let lastMorningHour = 11
private let lastAfternoonHour = 17

/// "Good morning" (0–11), "Good afternoon" (12–17), or "Good evening" (18–23).
func timeBasedGreeting(
    hour: Int = Calendar.current.component(.hour, from: Date()),
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    switch hour {
    case 0...lastMorningHour: L("Good morning", locale: locale)
    case (lastMorningHour + 1)...lastAfternoonHour: L("Good afternoon", locale: locale)
    default: L("Good evening", locale: locale)
    }
}
