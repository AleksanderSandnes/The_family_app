// Profile — the iOS twin of ProfileScreens.kt: hero gradient card with tappable
// avatar (camera strip / upload overlay), info rows, edit/settings actions, sign out,
// avatar source dialog (camera / gallery / remove), and the edit screen.
import NukeUI
import PhotosUI
import SwiftUI

struct ProfileScreen: View {
    let viewModel: ProfileViewModel
    let onEdit: () -> Void
    let onSettings: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var showAvatarPicker = false
    @State private var showCamera = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showGallery = false

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ErrorBanner(message: viewModel.error)
                heroCard

                VStack(spacing: 0) {
                    InfoRow(
                        systemImage: "envelope.fill",
                        label: "Email",
                        value: displayValue(viewModel.user?.email)
                    )
                    InfoRow(
                        systemImage: "phone.fill",
                        label: "Mobile",
                        value: displayValue(viewModel.user?.mobile)
                    )
                    InfoRow(
                        systemImage: "birthday.cake.fill",
                        label: "Birthday",
                        value: formatBirthday(viewModel.user?.birthday)
                    )
                }
                .padding(6)
                .glassCard(cornerRadius: Radius.overviewCard)

                ActionRow(systemImage: "pencil", label: "Edit profile", onTap: onEdit)
                ActionRow(systemImage: "gearshape.fill", label: "Settings", onTap: onSettings)

                DestructiveButton(text: "Sign out", systemImage: "rectangle.portrait.and.arrow.right") {
                    viewModel.signOut()
                }
                .padding(.top, Spacing.xs)
            }
            .padding(Spacing.screenEdge)
        }
        .ambientBackground()
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.large)
        .resumeEffect { viewModel.refresh() }
        .confirmationDialog("Profile photo", isPresented: $showAvatarPicker, titleVisibility: .visible) {
            Button("Take photo") { showCamera = true }
            Button("Choose from gallery") { showGallery = true }
            if viewModel.user?.avatarUrl != nil {
                Button("Remove photo", role: .destructive) { viewModel.removeAvatar() }
            }
            Button("Cancel", role: .cancel) {}
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { image in
                viewModel.saveAvatar(image: image)
            }
            .ignoresSafeArea()
        }
        .photosPicker(isPresented: $showGallery, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    viewModel.saveAvatar(imageData: data)
                }
                photoItem = nil
            }
        }
    }

    private func displayValue(_ value: String?) -> String {
        let trimmed = value?.trimmingCharacters(in: .whitespaces) ?? ""
        return trimmed.isEmpty ? "—" : trimmed
    }

    private var heroCard: some View {
        HStack(spacing: Spacing.lg) {
            Button {
                showAvatarPicker = true
            } label: {
                avatarView
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isUploading)

            VStack(alignment: .leading, spacing: 3) {
                Text(viewModel.user?.name ?? "")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(.white)
                Text(viewModel.user?.email ?? "")
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(Spacing.xxl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Gradients.hero(dark: colorScheme == .dark))
        .clipShape(RoundedRectangle(cornerRadius: Radius.bigCard, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.bigCard, style: .continuous)
                .strokeBorder(Color.white.opacity(0.22), lineWidth: 0.5)
        )
        .shadow(
            color: (colorScheme == .dark ? Color.black : Palette.indigo600).opacity(0.28),
            radius: 16,
            y: 10
        )
    }

    private var avatarView: some View {
        ZStack(alignment: .bottom) {
            Group {
                if let avatarUrl = viewModel.user?.avatarUrl, let url = URL(string: avatarUrl) {
                    LazyImage(url: url) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        } else {
                            initialFallback
                        }
                    }
                } else {
                    initialFallback
                }
            }
            .frame(width: 72, height: 72)
            .clipShape(Circle())

            if viewModel.isUploading {
                Circle()
                    .fill(Color.black.opacity(0.55))
                    .frame(width: 72, height: 72)
                    .overlay(ProgressView().tint(.white))
            } else {
                // Camera strip along the bottom of the avatar.
                Rectangle()
                    .fill(Color.black.opacity(0.38))
                    .frame(width: 72, height: 20)
                    .overlay(
                        Image(systemName: "camera.fill")
                            .font(.system(size: 10))
                            .foregroundStyle(.white)
                    )
                    .clipShape(Circle().path(in: CGRect(x: 0, y: -52, width: 72, height: 72)))
            }
        }
        .frame(width: 72, height: 72)
        .clipShape(Circle())
        .accessibilityLabel("Change profile photo")
    }

    private var initialFallback: some View {
        ZStack {
            Circle().fill(Color.white.opacity(0.2))
            Text(String(viewModel.user?.name.trimmingCharacters(in: .whitespaces).first ?? "?").uppercased())
                .font(.headlineMedium.weight(.bold))
                .foregroundStyle(.white)
        }
    }
}

private struct InfoRow: View {
    let systemImage: String
    let label: String
    let value: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .foregroundStyle(Color.appPrimary)
                .frame(width: 24)
            Text(label)
                .font(.system(size: 14))
                .foregroundStyle(Color.appOnSurfaceVariant)
            Spacer()
            Text(value)
                .font(.system(size: 14.5, weight: .semibold))
                .foregroundStyle(Color.appOnSurface)
                .lineLimit(1)
        }
        .padding(14)
    }
}

private struct ActionRow: View {
    let systemImage: String
    let label: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .foregroundStyle(Color.appPrimary)
                    .frame(width: 24)
                Text(label)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.appOnSurface)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(18)
            .glassCard(cornerRadius: Radius.row)
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}

// MARK: - Edit screen

struct ProfileEditScreen: View {
    let viewModel: ProfileViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var mobile = ""
    @State private var birthday = ""
    @State private var seeded = false

    private var saveEnabled: Bool {
        !name.isEmpty && !email.isEmpty && !mobile.isEmpty && !birthday.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text("PERSONAL INFORMATION")
                    .font(.sectionLabel)
                    .tracking(0.6)
                    .foregroundStyle(Color.appCaption)
                FamilyTextField(
                    label: "Full name *",
                    text: $name,
                    systemImage: "person",
                    autocapitalization: .words
                )
                FamilyTextField(
                    label: "Email *",
                    text: $email,
                    systemImage: "envelope",
                    keyboardType: .emailAddress,
                    autocapitalization: .never
                )
                FamilyTextField(
                    label: "Mobile *",
                    text: $mobile,
                    systemImage: "phone",
                    keyboardType: .phonePad
                )
                BirthdayPickerField(isoDate: $birthday, label: "Birthday *")
                PrimaryButton(text: "Save changes", enabled: saveEnabled) {
                    viewModel.save(name: name, email: email, birthday: birthday, mobile: mobile)
                    dismiss()
                }
                .padding(.top, Spacing.xs)
            }
            .padding(Spacing.lg)
        }
        .ambientBackground()
        .featureTopBar("Edit profile")
        .onAppear {
            guard !seeded, let user = viewModel.user else { return }
            seeded = true
            name = user.name
            email = user.email
            mobile = user.mobile
            birthday = user.birthday
        }
    }
}
