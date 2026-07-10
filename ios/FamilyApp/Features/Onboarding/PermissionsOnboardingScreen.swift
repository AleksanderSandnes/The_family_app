// Permissions primer: explains why the app asks for notifications and location before
// firing the system prompts, then marks onboarding complete so the gate opens.
import CoreLocation
import SwiftUI
import UserNotifications

private struct PermissionCard: Identifiable {
    let systemImage: String
    let title: String
    let subtitle: String

    var id: String {
        title
    }
}

private let permissionCards: [PermissionCard] = [
    PermissionCard(
        systemImage: "bell.badge.fill",
        title: "Notifications",
        subtitle: "Chat messages, birthday and event reminders."
    ),
    PermissionCard(
        systemImage: "location.fill",
        title: "Location",
        subtitle: "Share where you are on the family map — only with your family, only when you choose."
    ),
    PermissionCard(
        systemImage: "camera.fill",
        title: "Camera & photos",
        subtitle: "Profile pictures and photo sharing in chat. Asked when you first use them."
    ),
]

struct PermissionsOnboardingScreen: View {
    let onComplete: () -> Void

    @State private var requesting = false
    @State private var locationManager = CLLocationManager()

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: Spacing.lg) {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Color.appPrimaryContainer)
                        .frame(width: 72, height: 72)
                        .overlay(
                            Image(systemName: "hand.raised.fill")
                                .font(.system(size: 30))
                                .foregroundStyle(Color.appPrimary)
                        )
                        .padding(.top, 48)
                    Text("Before we start")
                        .font(.headlineMedium)
                        .foregroundStyle(Color.appOnBackground)
                    Text(
                        // swiftlint:disable:next line_length
                        "The Family App works best with a couple of permissions. You stay in control — everything can be changed later in Settings."
                    )
                    .font(.bodyMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Spacing.lg)

                    VStack(spacing: Spacing.cardGap) {
                        ForEach(permissionCards) { card in
                            HStack(spacing: Spacing.md) {
                                Image(systemName: card.systemImage)
                                    .font(.system(size: 20))
                                    .foregroundStyle(Color.appPrimary)
                                    .frame(width: 44, height: 44)
                                    .background(Color.appPrimaryContainer)
                                    .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(LocalizedStringKey(card.title))
                                        .font(.titleMedium)
                                        .foregroundStyle(Color.appOnSurface)
                                    Text(LocalizedStringKey(card.subtitle))
                                        .font(.bodyMedium)
                                        .foregroundStyle(Color.appOnSurfaceVariant)
                                }
                                Spacer()
                            }
                            .padding(Spacing.lg)
                            .background(Color.appSurface)
                            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                        }
                    }
                    .padding(.horizontal, Spacing.screenEdge)
                }
            }

            VStack(spacing: Spacing.sm) {
                PrimaryButton(text: L("Allow permissions"), loading: requesting) {
                    requestPermissions()
                }
                Button("Maybe later") { onComplete() }
                    .font(.bodyMedium.weight(.semibold))
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            .padding(Spacing.screenEdge)
        }
        .background(Color.appBackground)
    }

    private func requestPermissions() {
        requesting = true
        Task {
            _ = try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])
            // When-in-use prompt; the map screen escalates if background sharing is enabled.
            locationManager.requestWhenInUseAuthorization()
            requesting = false
            onComplete()
        }
    }
}
