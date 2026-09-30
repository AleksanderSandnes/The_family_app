import SwiftUI

struct ConversationTitle: View {
    let title: String
    let presence: String?

    var body: some View {
        VStack(spacing: 0) {
            Text(title)
                .font(.titleMedium)
                .foregroundStyle(Color.appOnSurface)
            if let presence {
                Text(presence)
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
        }
    }
}
