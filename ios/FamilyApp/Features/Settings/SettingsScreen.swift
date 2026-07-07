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
    private let locationService = LocationSharingService.shared
    private var session: SessionStore {
        SessionStore.shared
    }

    @State private var showSaved = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                SectionHeader(text: L("Appearance"))
                settingsCard {
                    ThemeSelector(selected: session.themeMode) { mode in
                        repo.setThemeMode(mode)
                        flashSaved()
                    }
                }

                SectionHeader(text: L("Notifications"))
                settingsCard {
                    ToggleRow(
                        systemImage: "bell.fill",
                        title: L("Notifications"),
                        subtitle: L("Family activity and reminders"),
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

                SectionHeader(text: L("Privacy"))
                settingsCard {
                    ToggleRow(
                        systemImage: "location.fill",
                        title: L("Visible on family map"),
                        subtitle: locationService.permissionAllowsSharing
                            ? L("Share your live location with family")
                            : L("Location access is off — turn it on in iOS Settings"),
                        // Reflects the real system permission: if location is denied the
                        // toggle reads off regardless of the saved preference.
                        isOn: Binding(
                            get: { locationService.isSharing },
                            set: { visible in
                                if visible { locationService.enableSharing() }
                                else { locationService.disableSharing() }
                                flashSaved()
                            }
                        )
                    )
                }

                SectionHeader(text: L("Language"))
                settingsCard {
                    LanguagePicker(selected: session.appLanguage) { language in
                        session.setAppLanguage(language)
                        flashSaved()
                    }
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
        }
        .ambientBackground()
        .featureTopBar(L("Settings"))
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
            let granted = await (try? UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            await repo.setNotificationsEnabled(granted)
        }
    }

    private func settingsCard(@ViewBuilder content: () -> some View) -> some View {
        VStack(spacing: 0) { content() }
            .glassCard(cornerRadius: Radius.overviewCard)
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
                        Text(LocalizedStringKey(option.label))
                            .font(.system(size: 12.5, weight: selected ? .bold : .medium))
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background {
                                if selected {
                                    Capsule().fill(Color.appPrimary)
                                        .shadow(color: Color.appPrimary.opacity(0.3), radius: 8, y: 3)
                                } else {
                                    Capsule().fill(Color.appSurface.opacity(0.55))
                                }
                            }
                            .foregroundStyle(selected ? Color.white : Color.appOnSurfaceVariant)
                    }
                }
            }
        }
        .padding(Spacing.lg)
    }
}

/// Language dropdown — English / Norsk. Endonyms shown in their own language.
private struct LanguagePicker: View {
    let selected: AppLanguage
    let onSelect: (AppLanguage) -> Void

    var body: some View {
        Menu {
            ForEach(AppLanguage.allCases) { language in
                Button {
                    onSelect(language)
                } label: {
                    if language == selected {
                        Label(language.displayName, systemImage: "checkmark")
                    } else {
                        Text(language.displayName)
                    }
                }
            }
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: "globe")
                    .foregroundStyle(Color.appPrimary)
                    .frame(width: 24)
                Text("Language")
                    .font(.system(size: 16))
                    .foregroundStyle(Color.appOnSurface)
                Spacer()
                Text(selected.displayName)
                    .font(.system(size: 15))
                    .foregroundStyle(Color.appPrimary)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(Spacing.lg)
        }
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
                        VStack(spacing: 5) {
                            Image(systemName: icon(for: mode))
                                .font(.system(size: 18))
                            Text(LocalizedStringKey(mode.label))
                                .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                        }
                        .frame(maxWidth: .infinity, minHeight: 60)
                        .background(
                            RoundedRectangle(cornerRadius: Radius.badgeLarge, style: .continuous)
                                .fill(isSelected ? Color.appPrimary.opacity(0.1) : Color.appSurface.opacity(0.55))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: Radius.badgeLarge, style: .continuous)
                                .strokeBorder(isSelected ? Color.appPrimary.opacity(0.5) : .clear, lineWidth: 1.5)
                        )
                        .foregroundStyle(isSelected ? Color.appPrimary : Color.appOnSurfaceVariant)
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
