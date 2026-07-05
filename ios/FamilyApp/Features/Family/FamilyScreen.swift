// Family — the iOS twin of FamilyScreen.kt: no-family CTA state, family header card
// (avatar stack, copyable invite code, share sheet, QR), member list with admin
// swipe-remove, leave-family confirm, create/join dialogs, family photo change.
import NukeUI
import PhotosUI
import SwiftUI

struct FamilyScreen: View {
    let viewModel: FamilyViewModel

    @State private var showCreate = false
    @State private var showJoin = false
    @State private var showLeaveConfirm = false
    @State private var memberToRemove: UserModel?
    @State private var joinCodeText = ""
    @State private var showQr = false
    @State private var photoItem: PhotosPickerItem?

    private var isAdmin: Bool {
        viewModel.family != nil && viewModel.family?.adminId == viewModel.currentUser?.id
    }

    var body: some View {
        Group {
            if let family = viewModel.family {
                familyContent(family)
            } else {
                noFamilyContent
            }
        }
        .background(Color.appBackground)
        .navigationTitle("Family")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if viewModel.family != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            Text("Change family photo")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .accessibilityLabel("More options")
                    }
                }
            }
        }
        .resumeEffect {
            viewModel.refresh()
            // An invite deep link routes here — open the join flow pre-filled.
            if let code = viewModel.consumePendingJoinCode() {
                joinCodeText = code
                showJoin = true
            }
        }
        .onChange(of: viewModel.pendingJoinCode) { _, code in
            guard code != nil, let consumed = viewModel.consumePendingJoinCode() else { return }
            joinCodeText = consumed
            showJoin = true
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    viewModel.uploadFamilyPhoto(data)
                }
                photoItem = nil
            }
        }
        .alert("Leave family?", isPresented: $showLeaveConfirm) {
            Button("Leave", role: .destructive) { viewModel.leaveFamily() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You will lose access to shared data. You can rejoin later with the invite code.")
        }
        .alert(
            "Remove member?",
            isPresented: Binding(
                get: { memberToRemove != nil },
                set: { if !$0 { memberToRemove = nil } }
            )
        ) {
            Button("Remove", role: .destructive) {
                if let member = memberToRemove { viewModel.removeMember(member.id) }
                memberToRemove = nil
            }
            Button("Cancel", role: .cancel) { memberToRemove = nil }
        } message: {
            Text("\(memberToRemove?.name ?? "This member") will be removed from the family. They can rejoin later with the invite code.")
        }
        .sheet(isPresented: $showCreate) {
            CreateFamilySheet { name, code in
                viewModel.createFamily(name: name, code: code)
                showCreate = false
            }
        }
        .alert("Join a family", isPresented: $showJoin) {
            TextField("Invite code", text: $joinCodeText)
                .textInputAutocapitalization(.characters)
            Button("Join") {
                viewModel.joinFamily(code: joinCodeText)
                joinCodeText = ""
            }
            Button("Cancel", role: .cancel) {
                joinCodeText = ""
                viewModel.clearError()
            }
        }
        .sheet(isPresented: $showQr) {
            if let family = viewModel.family {
                QrSheet(family: family)
            }
        }
    }

    // MARK: - No family

    private var noFamilyContent: some View {
        VStack(spacing: Spacing.md) {
            EmptyState(
                systemImage: "figure.2.and.child.holdinghands",
                title: "Bring your family together",
                subtitle: "Create a family space or join one with an invite code to share calendars, shopping lists, wishlists, and more."
            )
            ErrorBanner(message: viewModel.error)
            PrimaryButton(text: "Create a Family", systemImage: "person.3.fill") {
                showCreate = true
            }
            SecondaryButton(text: "Join with Invite Code", systemImage: "person.badge.plus") {
                joinCodeText = ""
                showJoin = true
            }
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            LinearGradient(
                colors: [Color.appPrimaryContainer.opacity(0.35), Color.appBackground],
                startPoint: .top,
                endPoint: .center
            )
        )
    }

    // MARK: - Has family

    private func familyContent(_ family: FamilyModel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.cardGap) {
                headerCard(family)

                Text("Members")
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnBackground)
                    .padding(.top, Spacing.sm)

                ForEach(viewModel.members) { member in
                    let memberIsAdmin = member.id == family.adminId
                    MemberCard(member: member, isAdmin: memberIsAdmin)
                        .contextMenu {
                            if isAdmin && !memberIsAdmin && member.id != viewModel.currentUser?.id {
                                Button("Remove from family", role: .destructive) {
                                    memberToRemove = member
                                }
                            }
                        }
                }

                DestructiveButton(text: "Leave family", systemImage: "rectangle.portrait.and.arrow.right") {
                    showLeaveConfirm = true
                }
                .padding(.top, Spacing.sm)
            }
            .padding(Spacing.screenEdge)
        }
        .refreshable { viewModel.refresh() }
    }

    private func headerCard(_ family: FamilyModel) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(family.name)
                .font(.headlineMedium)
                .foregroundStyle(Color.appOnSurface)
            AvatarStack(members: viewModel.members)
            Text("\(viewModel.members.count) member\(viewModel.members.count == 1 ? "" : "s")")
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)

            if !family.joinCode.isEmpty {
                CopyableCodeField(code: family.joinCode)
                    .padding(.top, Spacing.sm)
                ShareLink(item: inviteMessage(familyName: family.name, joinCode: family.joinCode)) {
                    shareLabel("Share invite", systemImage: "square.and.arrow.up")
                }
                Button {
                    showQr = true
                } label: {
                    shareLabel("Show QR code", systemImage: "qrcode")
                }
            }
            if viewModel.isUploading {
                HStack(spacing: Spacing.sm) {
                    ProgressView()
                    Text("Uploading photo…")
                        .font(.labelMedium)
                        .foregroundStyle(Color.appOnSurfaceVariant)
                }
            }
            ErrorBanner(message: viewModel.error)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.xl)
        .background(Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: Elevation.resting, y: 1)
    }

    private func shareLabel(_ text: String, systemImage: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: systemImage)
                .foregroundStyle(Color.appPrimary)
            Text(text)
                .font(.titleMedium)
                .foregroundStyle(Color.appOnSurface)
        }
        .frame(maxWidth: .infinity, minHeight: 48)
        .overlay(
            RoundedRectangle(cornerRadius: Radius.button, style: .continuous)
                .strokeBorder(Color.appOutline, lineWidth: 1)
        )
    }
}

// MARK: - Pieces

/// Overlapping avatar stack showing up to 4 avatars, then a "+N" overflow badge.
private struct AvatarStack: View {
    let members: [UserModel]

    var body: some View {
        HStack(spacing: -10) {
            ForEach(members.prefix(4)) { member in
                InitialAvatar(user: member, size: 40)
                    .overlay(Circle().strokeBorder(Color.appSurface, lineWidth: 2))
            }
            if members.count > 4 {
                Text("+\(members.count - 4)")
                    .font(.labelMedium.weight(.semibold))
                    .frame(width: 40, height: 40)
                    .background(Color.appSurfaceVariant)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appSurface, lineWidth: 2))
            }
        }
    }
}

private struct CopyableCodeField: View {
    let code: String

    @State private var copied = false

    var body: some View {
        Button {
            UIPasteboard.general.string = code
            copied = true
            Task {
                try? await Task.sleep(for: .seconds(2))
                copied = false
            }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Invite Code")
                        .font(.labelMedium)
                        .foregroundStyle(Color.appOnSurfaceVariant)
                    Text(code)
                        .font(.titleLarge.weight(.bold))
                        .foregroundStyle(Color.appOnSurface)
                        .textSelection(.enabled)
                }
                Spacer()
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .foregroundStyle(Color.appPrimary)
            }
            .padding(Spacing.lg)
            .background(Color.appSurfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Family invite code: \(code)")
    }
}

private struct MemberCard: View {
    let member: UserModel
    let isAdmin: Bool

    var body: some View {
        HStack(spacing: Spacing.md) {
            InitialAvatar(user: member, size: 44)
            VStack(alignment: .leading, spacing: 1) {
                Text(member.name)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text(isAdmin ? "Admin" : "Member")
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
            }
            Spacer()
            if isAdmin {
                Image(systemName: "crown.fill")
                    .foregroundStyle(Palette.amber500)
            }
        }
        .padding(Spacing.lg)
        .background(Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
        .accessibilityLabel("\(member.name), \(isAdmin ? "Admin" : "Member")")
    }
}

private struct CreateFamilySheet: View {
    let onCreate: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var code = generateJoinCode()

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text("Create a family")
                .font(.titleLarge)
                .foregroundStyle(Color.appOnSurface)
                .padding(.top, Spacing.xxl)
            FamilyTextField(label: "Family name", text: $name, systemImage: "person.3")
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Invite code")
                        .font(.labelMedium)
                        .foregroundStyle(Color.appOnSurfaceVariant)
                    Text(code)
                        .font(.titleLarge.weight(.bold))
                        .foregroundStyle(Color.appOnSurface)
                }
                Spacer()
                Button {
                    code = generateJoinCode()
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundStyle(Color.appPrimary)
                }
                .accessibilityLabel("Generate new code")
            }
            .padding(Spacing.lg)
            .background(Color.appSurfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
            Spacer()
            PrimaryButton(text: "Create", enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                onCreate(name.trimmingCharacters(in: .whitespaces), code)
                dismiss()
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.bottom, Spacing.lg)
        .presentationDetents([.medium])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
    }
}

private struct QrSheet: View {
    let family: FamilyModel

    var body: some View {
        VStack(spacing: Spacing.md) {
            Text("Scan to join")
                .font(.titleLarge)
                .foregroundStyle(Color.appOnSurface)
                .padding(.top, Spacing.xxl)
            if let qr = generateQrImage(
                content: DeepLinkURL.invite(code: family.joinCode).absoluteString
            ) {
                Image(uiImage: qr)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(width: 220, height: 220)
                    .accessibilityLabel("Family invite QR code")
            }
            Text(family.joinCode)
                .font(.titleMedium)
                .foregroundStyle(Color.appOnSurface)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .presentationDetents([.medium])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
    }
}
