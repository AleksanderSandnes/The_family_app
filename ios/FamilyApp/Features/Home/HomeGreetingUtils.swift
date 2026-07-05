// Time-of-day greeting — mirrors HomeGreetingUtils.kt.
import Foundation

private let lastMorningHour = 11
private let lastAfternoonHour = 17

/// "Good morning" (0–11), "Good afternoon" (12–17), or "Good evening" (18–23).
func timeBasedGreeting(hour: Int = Calendar.current.component(.hour, from: Date())) -> String {
    switch hour {
    case 0...lastMorningHour: "Good morning"
    case (lastMorningHour + 1)...lastAfternoonHour: "Good afternoon"
    default: "Good evening"
    }
}
