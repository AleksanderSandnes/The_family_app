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
    @State private var showPhotoPicker = false
    @State private var selectedMember: UserModel?

    @Environment(\.colorScheme) private var colorScheme

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
        .ambientBackground()
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItem, matching: .images)
        .sheet(item: $selectedMember) { member in
            MemberProfileSheet(
                member: member,
                isSelf: member.id == viewModel.currentUser?.id,
                relation: viewModel.relations[member.id] ?? ""
            ) { newRelation in
                viewModel.setRelation(toUserId: member.id, relation: newRelation)
            }
        }
        .sheet(isPresented: Binding(
            get: { viewModel.promptRelationSetup },
            set: { viewModel.promptRelationSetup = $0 }
        )) {
            RelationsSetupSheet(
                members: viewModel.members.filter { $0.id != viewModel.currentUser?.id },
                relations: viewModel.relations
            ) { toUserId, relation in
                viewModel.setRelation(toUserId: toUserId, relation: relation)
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
            Text(
                "\(memberToRemove?.name ?? L("This member")) will be removed from the family. They can rejoin later with the invite code."
            )
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
                title: L("Bring your family together"),
                subtitle: L(
                    // swiftlint:disable:next line_length
                    "Create a family space or join one with an invite code to share calendars, shopping lists, wishlists, and more."
                )
            )
            ErrorBanner(message: viewModel.error)
            PrimaryButton(text: L("Create a Family"), systemImage: "person.3.fill") {
                showCreate = true
            }
            SecondaryButton(text: L("Join with Invite Code"), systemImage: "person.badge.plus") {
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
                ScreenHeader(L("Family")) {
                    Menu {
                        // PhotosPicker inside a Menu is a no-op on iOS — trigger it via state instead.
                        Button { showPhotoPicker = true } label: {
                            Label(L("Change family photo"), systemImage: "photo")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .font(.system(size: 24))
                            .accessibilityLabel("More options")
                    }
                }

                headerCard(family)

                SectionHeader(text: L("Members"))
                    .padding(.top, Spacing.sm)

                ForEach(viewModel.members) { member in
                    let memberIsAdmin = member.id == family.adminId
                    Button { selectedMember = member } label: {
                        MemberCard(
                            member: member,
                            isAdmin: memberIsAdmin,
                            relation: viewModel.relations[member.id]
                        )
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        if isAdmin, !memberIsAdmin, member.id != viewModel.currentUser?.id {
                            Button(role: .destructive) {
                                memberToRemove = member
                            } label: {
                                Label(L("Remove from family"), systemImage: "person.badge.minus")
                            }
                        }
                    }
                }

                DestructiveButton(text: L("Leave family"), systemImage: "rectangle.portrait.and.arrow.right") {
                    showLeaveConfirm = true
                }
                .padding(.top, Spacing.sm)
            }
            .padding(Spacing.screenEdge)
        }
        .refreshable { viewModel.refresh() }
    }

    private func headerCard(_ family: FamilyModel) -> some View {
        VStack(spacing: Spacing.lg) {
            VStack(spacing: Spacing.md) {
                familyPhotoHero(family)
                VStack(spacing: 4) {
                    Text(family.name)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundStyle(Color.appOnSurface)
                        .multilineTextAlignment(.center)
                    Text(viewModel.members.count == 1 ? L("1 member") : L("\(viewModel.members.count) members"))
                        .font(.subheadline)
                        .foregroundStyle(Color.appCaption)
                }
                if !viewModel.members.isEmpty {
                    AvatarStack(members: viewModel.members)
                        .padding(.top, 2)
                }
            }
            .frame(maxWidth: .infinity)

            if !family.joinCode.isEmpty {
                CopyableCodeField(code: family.joinCode)
                HStack(spacing: Spacing.sm) {
                    ShareLink(item: inviteMessage(
                        familyName: family.name,
                        joinCode: family.joinCode,
                        locale: appLocale
                    )) {
                        shareLabel(L("Share invite"), systemImage: "square.and.arrow.up")
                    }
                    Button {
                        showQr = true
                    } label: {
                        shareLabel(L("QR code"), systemImage: "qrcode")
                    }
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
        .frame(maxWidth: .infinity)
        .padding(Spacing.xl)
        .glassCard(cornerRadius: Radius.bigCard)
    }

    /// Large family portrait at the top of the header. Admins can tap it (or the camera
    /// badge) to swap the photo; everyone else sees it as a static hero.
    @ViewBuilder
    private func familyPhotoHero(_ family: FamilyModel) -> some View {
        if isAdmin {
            Button { showPhotoPicker = true } label: { photoCircle(family) }
                .buttonStyle(.plain)
        } else {
            photoCircle(family)
        }
    }

    private func photoCircle(_ family: FamilyModel) -> some View {
        let size: CGFloat = 112
        return ZStack {
            Gradients.hero(dark: colorScheme == .dark)
            if let photoUrl = family.photoUrl, let url = URL(string: photoUrl) {
                LazyImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        familyPhotoFallback
                    }
                }
            } else {
                familyPhotoFallback
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().strokeBorder(Color.appSurface, lineWidth: 3))
        .overlay(alignment: .bottomTrailing) {
            if isAdmin {
                ZStack {
                    Circle().fill(Color.appPrimary)
                    Image(systemName: "camera.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 34, height: 34)
                .overlay(Circle().strokeBorder(Color.appSurface, lineWidth: 2.5))
            }
        }
        .shadow(color: (colorScheme == .dark ? Color.black : Palette.indigo600).opacity(0.28), radius: 14, y: 6)
    }

    private var familyPhotoFallback: some View {
        Image(systemName: "person.3.fill")
            .font(.system(size: 42, weight: .medium))
            .foregroundStyle(.white)
    }

    private func shareLabel(_ text: String, systemImage: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: systemImage)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.appPrimary)
            Text(text)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.appOnSurface)
        }
        .frame(maxWidth: .infinity, minHeight: 46)
        .glassCard(cornerRadius: Radius.badgeLarge)
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
                VStack(alignment: .leading, spacing: 2) {
                    Text("INVITE CODE")
                        .font(.system(size: 10.5, weight: .bold))
                        .tracking(0.7)
                        .foregroundStyle(Color.appCaption)
                    Text(code)
                        .font(.system(size: 18, weight: .bold, design: .monospaced))
                        .tracking(2)
                        .foregroundStyle(Color.appOnSurface)
                        .textSelection(.enabled)
                }
                Spacer()
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .foregroundStyle(Color.appPrimary)
            }
            .padding(Spacing.lg)
            .background(
                Color.appPrimary.opacity(0.09),
                in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Family invite code: \(code)")
    }
}

private struct MemberCard: View {
    let member: UserModel
    let isAdmin: Bool
    var relation: String?

    var body: some View {
        HStack(spacing: Spacing.md) {
            InitialAvatar(user: member, size: 42)
            VStack(alignment: .leading, spacing: 2) {
                Text(member.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.appOnSurface)
                if let relation, !relation.isEmpty {
                    Text(L(dynamic: relation))
                        .font(.caption)
                        .foregroundStyle(Color.appCaption)
                }
            }
            Spacer()
            if isAdmin {
                Text("Admin")
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(Color.appPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.appPrimary.opacity(0.12), in: Capsule())
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.appCaption)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 12)
        .glassCard(cornerRadius: Radius.row)
        .accessibilityLabel("\(member.name), \(isAdmin ? L("Admin") : L("Member"))")
    }
}

/// Family relation options (relative to the viewer).
let familyRelationOptions = [
    "Mom", "Dad", "Son", "Daughter", "Sister", "Brother",
    "Wife", "Husband", "Fiancé", "Partner",
    "Grandmother", "Grandfather", "Grandchild",
    "Aunt", "Uncle", "Cousin", "Friend", "Other",
]

private struct CreateFamilySheet: View {
    let onCreate: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var code = generateJoinCode()

    private var canCreate: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(title: L("Create a family"), confirmTitle: L("Create"), confirmEnabled: canCreate) {
                dismiss()
            } onConfirm: {
                onCreate(name.trimmingCharacters(in: .whitespaces), code)
                dismiss()
            }
            GlassField(systemImage: "person.3", placeholder: L("Family name"), text: $name)
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("INVITE CODE")
                        .font(.system(size: 10.5, weight: .bold))
                        .tracking(0.7)
                        .foregroundStyle(Color.appCaption)
                    Text(code)
                        .font(.system(size: 18, weight: .bold, design: .monospaced))
                        .tracking(2)
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
            .background(
                Color.appPrimary.opacity(0.09),
                in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
            )
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
    }
}

private struct QrSheet: View {
    let family: FamilyModel

    var body: some View {
        VStack(spacing: Spacing.lg) {
            VStack(spacing: 4) {
                Text("Scan to join")
                    .font(.pushedTitle)
                    .foregroundStyle(Color.appOnSurface)
                Text("Point a camera at the code to join the family")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(.top, Spacing.xl)

            if let qr = generateQrImage(
                content: DeepLinkURL.invite(code: family.joinCode).absoluteString
            ) {
                Image(uiImage: qr)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(width: 224, height: 224)
                    .padding(Spacing.xl)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: Radius.medium, style: .continuous))
                    .shadow(color: Color(hex: 0x141A3C).opacity(0.12), radius: 15, y: 8)
                    .accessibilityLabel("Family invite QR code")
            }
            Text(family.joinCode)
                .font(.system(size: 16, weight: .bold, design: .monospaced))
                .tracking(3)
                .foregroundStyle(Color.appOnSurface)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
    }
}
