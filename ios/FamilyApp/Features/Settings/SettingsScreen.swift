// Settings — the iOS twin of SettingsScreen.kt/SettingsViewModel.kt: theme selector,
// notifications toggle + reminder lead-time chips, map-visibility toggle, about card.
// The lead time mirrors to users.notify_days_before so the server-side daily-reminders
// function honours it.
import SwiftUI
import UserNotifications

let leadTimeOptions: [(label: String, days: Int)] = [
    ("Same day", 0),
    ("1 day", 1),
    ("2 days", 2),
    ("7 days", 7),
]

struct SettingsScreen: View {
    private let repo = FamilyRepository.shared
    private var session: SessionStore { SessionStore.shared }

    @State private var showSaved = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                SectionHeader(text: "Appearance")
                settingsCard {
                    ThemeSelector(selected: session.themeMode) { mode in
                        repo.setThemeMode(mode)
                        flashSaved()
                    }
                }

                SectionHeader(text: "Notifications")
                settingsCard {
                    ToggleRow(
                        systemImage: "bell.fill",
                        title: "Notifications",
                        subtitle: "Family activity and reminders",
                        isOn: Binding(
                            get: { session.notificationsEnabled },
                            set: { enabled in
                                if enabled {
                                    requestNotificationPermission()
                                } else {
                                    Task { await repo.setNotificationsEnabled(false) }
                                }
                                flashSaved()
                            }
                        )
                    )
                    if session.notificationsEnabled {
                        Divider().padding(.horizontal, Spacing.lg)
                        leadTimeSelector
                    }
                }

                SectionHeader(text: "Privacy")
                settingsCard {
                    ToggleRow(
                        systemImage: "location.fill",
                        title: "Visible on family map",
                        subtitle: "Share your location with family",
                        isOn: Binding(
                            get: { session.locationVisible },
                            set: { visible in
                                repo.setLocationVisible(visible)
                                flashSaved()
                            }
                        )
                    )
                }

                SectionHeader(text: "About")
                settingsCard { aboutSection }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
        }
        .background(Color.appBackground)
        .featureTopBar("Settings")
        .overlay(alignment: .bottom) {
            if showSaved {
                Text("Settings saved")
                    .font(.bodyMedium)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.vertical, Spacing.sm)
                    .background(Color.appOnSurface.opacity(0.9))
                    .foregroundStyle(Color.appSurface)
                    .clipShape(Capsule())
                    .padding(.bottom, Spacing.xl)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func flashSaved() {
        withAnimation { showSaved = true }
        Task {
            try? await Task.sleep(for: .seconds(2))
            withAnimation { showSaved = false }
        }
    }

    private func requestNotificationPermission() {
        Task {
            let granted = (try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            await repo.setNotificationsEnabled(granted)
        }
    }

    private func settingsCard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .background(Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    private var leadTimeSelector: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text("Remind me")
                .font(.titleMedium)
                .foregroundStyle(Color.appOnSurface)
            HStack(spacing: Spacing.sm) {
                ForEach(leadTimeOptions, id: \.days) { option in
                    let selected = session.notifyDaysBefore == option.days
                    Button {
                        Task { await repo.setNotifyDaysBefore(option.days) }
                        flashSaved()
                    } label: {
                        Text(option.label)
                            .font(.labelMedium)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(selected ? Color.appPrimaryContainer : Color.appSurfaceVariant)
                            .foregroundStyle(
                                selected ? Color.appOnPrimaryContainer : Color.appOnSurfaceVariant
                            )
                            .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
                    }
                }
            }
        }
        .padding(Spacing.lg)
    }

    private var aboutSection: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: "info.circle.fill")
                .font(.system(size: 22))
                .foregroundStyle(Color.appOnPrimaryContainer)
                .frame(width: 48, height: 48)
                .background(Color.appPrimaryContainer)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text("The Family App")
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text("v\(appVersion) (build \(appBuild))")
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                Text("Your family, together")
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            Spacer()
        }
        .padding(Spacing.lg)
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }

    private var appBuild: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
    }
}

private struct ThemeSelector: View {
    let selected: ThemeMode
    let onSelect: (ThemeMode) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack(spacing: Spacing.md) {
                Image(systemName: "paintbrush.fill")
                    .foregroundStyle(Color.appPrimary)
                Text("Theme")
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
            }
            HStack(spacing: Spacing.sm) {
                ForEach(ThemeMode.allCases, id: \.self) { mode in
                    let isSelected = selected == mode
                    Button {
                        onSelect(mode)
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: icon(for: mode))
                            Text(mode.label)
                                .font(.labelMedium)
                        }
                        .frame(maxWidth: .infinity, minHeight: 60)
                        .background(isSelected ? Color.appPrimaryContainer : Color.appSurfaceVariant)
                        .foregroundStyle(
                            isSelected ? Color.appOnPrimaryContainer : Color.appOnSurfaceVariant
                        )
                        .clipShape(RoundedRectangle(cornerRadius: Radius.small, style: .continuous))
                    }
                }
            }
        }
        .padding(Spacing.lg)
    }

    private func icon(for mode: ThemeMode) -> String {
        switch mode {
        case .system: "iphone"
        case .light: "sun.max.fill"
        case .dark: "moon.fill"
        }
    }
}

private struct ToggleRow: View {
    let systemImage: String
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: systemImage)
                .foregroundStyle(Color.appPrimary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text(subtitle)
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Color.appPrimary)
        }
        .padding(Spacing.lg)
    }
}
