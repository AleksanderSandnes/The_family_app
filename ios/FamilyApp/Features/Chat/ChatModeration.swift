// Report/block moderation rules shared by the chat view model and screen
// (Play UGC policy / App Store 1.2). Backend: supabase/security/moderation.sql.
import Foundation

/// Report reasons accepted by the `report_message` RPC's check constraint.
enum ReportReason: String, CaseIterable, Identifiable {
    case spam
    case harassment
    case inappropriate
    case other

    var id: String {
        rawValue
    }

    /// English key for `L(_:)`.
    var label: String.LocalizationValue {
        switch self {
        case .spam: "Spam"
        case .harassment: "Harassment or bullying"
        case .inappropriate: "Inappropriate content"
        case .other: "Something else"
        }
    }
}

enum ChatModeration {
    /// Hides messages from blocked users; system messages always stay.
    static func visibleMessages(_ messages: [MessageModel], blocked: Set<String>) -> [MessageModel] {
        guard !blocked.isEmpty else { return messages }
        return messages.filter { $0.messageType == "system" || !blocked.contains($0.userFrom) }
    }

    /// Only other people's persisted, non-system messages can be reported or their sender blocked.
    static func canModerate(_ message: MessageModel, myId: String?) -> Bool {
        guard let myId else { return false }
        return message.userFrom != myId && message.messageType != "system" && !message.id.hasPrefix("temp-")
    }
}
