// Chat time/receipt helpers — the iOS twin of ChatTimeFormatters.kt. Pure and
// unit-tested; `now` is injectable for tests.
import Foundation

private let minuteMs: Int64 = 60000
private let hourMs: Int64 = 3600000
private let dayMs: Int64 = 86400000
private let presenceActiveNowMinutes: Int64 = 2
private let messageGroupGapMs: Int64 = 10 * minuteMs

func parseInstantMs(_ isoString: String) -> Int64? {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let date = formatter.date(from: isoString) {
        return Int64(date.timeIntervalSince1970 * 1000)
    }
    formatter.formatOptions = [.withInternetDateTime]
    guard let date = formatter.date(from: isoString) else { return nil }
    return Int64(date.timeIntervalSince1970 * 1000)
}

private func dayOfWeekShort(_ epochMs: Int64) -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "EEE"
    return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
}

private func monthDayShort(_ epochMs: Int64) -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "MMM d"
    return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
}

private func timePart(_ epochMs: Int64) -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "h:mm a"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter.string(from: Date(timeIntervalSince1970: Double(epochMs) / 1000))
}

/// Short relative label for the conversation list (e.g. "2m ago", "Yesterday", "Mon").
func relativeTime(_ isoString: String, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> String {
    guard let instant = parseInstantMs(isoString) else { return "" }
    let diffMs = nowMs - instant
    let diffMin = diffMs / minuteMs
    let diffH = diffMs / hourMs
    let diffD = diffMs / dayMs
    switch true {
    case diffMin < 1: return "now"
    case diffMin < 60: return "\(diffMin)m ago"
    case diffH < 24: return "\(diffH)h ago"
    case diffD == 1: return "Yesterday"
    case diffD < 7: return dayOfWeekShort(instant)
    default: return monthDayShort(instant)
    }
}

/// Compact message timestamp (e.g. "2:30 PM", "Yesterday 2:30 PM", "Mon 2:30 PM").
func messageTimeLabel(_ isoString: String, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> String {
    guard let instant = parseInstantMs(isoString) else { return "" }
    let diffD = (nowMs - instant) / dayMs
    let time = timePart(instant)
    switch true {
    case diffD == 0: return time
    case diffD == 1: return "Yesterday \(time)"
    case diffD < 7: return "\(dayOfWeekShort(instant)) \(time)"
    default: return "\(monthDayShort(instant)) \(time)"
    }
}

/// Chat-header presence: "Active now" within 2 minutes, else "Active {relative}".
func presenceLabel(_ lastActiveIso: String?, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> String? {
    guard let lastActiveIso, let instant = parseInstantMs(lastActiveIso) else { return nil }
    let diffMin = (nowMs - instant) / minuteMs
    return diffMin < presenceActiveNowMinutes
        ? "Active now"
        : "Active \(relativeTime(lastActiveIso, nowMs: nowMs))"
}

/// True when another participant's last_read_at is at or after the message's sent_at.
func messageSeen(otherLastRead: String?, sentAt: String) -> Bool {
    guard let otherLastRead,
          let read = parseInstantMs(otherLastRead),
          let sent = parseInstantMs(sentAt) else { return false }
    return read >= sent
}

/// True if the gap between two ISO timestamps exceeds 10 minutes (time separators).
func gapExceedsTenMinutes(earlierIso: String, laterIso: String) -> Bool {
    guard let earlier = parseInstantMs(earlierIso), let later = parseInstantMs(laterIso) else {
        return false
    }
    return later - earlier > messageGroupGapMs
}

// MARK: - Display-name / preview resolution (mirrors ConversationRow logic)

func conversationDisplayName(
    conversation: ConversationModel,
    participants: [UserModel],
    currentUserId: String
) -> String {
    let isOneOnOne = participants.count == 2 || conversation.userTo != nil
    let other = participants.first { $0.id != currentUserId }
    if isOneOnOne {
        if let name = other?.name, !name.isEmpty { return name }
        return conversation.name.isEmpty ? "Chat" : conversation.name
    }
    if !conversation.name.isEmpty { return conversation.name }
    let names = participants
        .filter { $0.id != currentUserId }
        .prefix(3)
        .compactMap { $0.name.split(separator: " ").first.map(String.init) }
        .joined(separator: ", ")
    return names.isEmpty ? "Group chat" : names
}

func conversationPreviewText(lastMessage: MessageModel?, lastSenderName: String?) -> String {
    guard let lastMessage else { return "No messages yet" }
    let prefix = lastSenderName.map { "\($0): " } ?? ""
    switch lastMessage.messageType {
    case "image": return "\(prefix)Photo"
    case "voice": return "\(prefix)Voice message"
    default: return "\(prefix)\(lastMessage.text)"
    }
}
