// Avatar with remote image or fallback initial + per-user color.
import NukeUI
import SwiftUI

struct InitialAvatar: View {
    let name: String
    let color: Color
    var size: CGFloat = 44
    var avatarUrl: String?

    var body: some View {
        Group {
            if let avatarUrl, let url = URL(string: avatarUrl) {
                LazyImage(url: url) { state in
                    if let image = state.image {
                        image.resizable().scaledToFill()
                    } else {
                        fallback
                    }
                }
            } else {
                fallback
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(name)
    }

    private var fallback: some View {
        ZStack {
            LinearGradient(
                colors: [color, color.opacity(0.7)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Text(initial)
                .font(.system(size: size * 0.42, weight: .bold))
                .foregroundStyle(.white)
        }
    }

    private var initial: String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = trimmed.first else { return "?" }
        return String(first).uppercased()
    }
}

extension InitialAvatar {
    /// Convenience for a UserModel — uses the shared avatar palette color.
    init(user: UserModel, size: CGFloat = 44) {
        self.init(
            name: user.name,
            // avatarColor 0 = unset → derive a stable colour from the name.
            color: Color(argb: user.avatarColor != 0
                ? user.avatarColor
                : FamilyRepository.palette(user.name)),
            size: size,
            avatarUrl: user.avatarUrl
        )
    }
}
