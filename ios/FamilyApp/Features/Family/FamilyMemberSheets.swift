// Member-facing sheets: the tap-a-member profile card and the post-join relations setup.
import SwiftUI

/// Tap-a-member profile: large avatar, name, email, phone, and (for others) an editable
/// "your relation to them" picker.
struct MemberProfileSheet: View {
    let member: UserModel
    let isSelf: Bool
    let relation: String
    let onSetRelation: (String) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: Spacing.lg) {
            HStack {
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 26))
                        .foregroundStyle(Color.appCaption)
                }
            }
            InitialAvatar(user: member, size: 96)
            Text(member.name)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Color.appOnSurface)

            VStack(spacing: 0) {
                if !member.email.isEmpty {
                    infoRow(icon: "envelope.fill", label: L("Email"), value: member.email)
                    Divider().padding(.leading, 52)
                }
                infoRow(
                    icon: "phone.fill", label: L("Phone"),
                    value: member.mobile.isEmpty ? L("Not set") : member.mobile
                )
                if !isSelf {
                    Divider().padding(.leading, 52)
                    relationRow
                }
            }
            .glassCard(cornerRadius: Radius.row)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
    }

    private var relationRow: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: "heart.fill")
                .font(.system(size: 16))
                .foregroundStyle(Color.appPrimary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 1) {
                Text(L("Your relation"))
                    .font(.caption)
                    .foregroundStyle(Color.appCaption)
                Menu {
                    ForEach(familyRelationOptions, id: \.self) { option in
                        Button(L(dynamic: option)) { onSetRelation(option) }
                    }
                    Divider()
                    Button(L("None"), role: .destructive) { onSetRelation("") }
                } label: {
                    HStack(spacing: 4) {
                        Text(relation.isEmpty ? L("Set relation") : L(dynamic: relation))
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(relation.isEmpty ? Color.appPrimary : Color.appOnSurface)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Color.appCaption)
                    }
                }
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 12)
    }

    private func infoRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundStyle(Color.appPrimary)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.caption)
                    .foregroundStyle(Color.appCaption)
                Text(value)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.appOnSurface)
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 12)
    }
}

/// Shown right after joining a family: set your relation to each existing member.
struct RelationsSetupSheet: View {
    let members: [UserModel]
    let relations: [String: String]
    let onSet: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var picked: [String: String] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            SheetHeader(
                title: L("Set your relations"),
                confirmTitle: L("Done"),
                confirmEnabled: true,
                onCancel: { dismiss() },
                onConfirm: { dismiss() }
            )
            Text(L("How are you related to each member? You can change this anytime."))
                .font(.caption)
                .foregroundStyle(Color.appCaption)
            VStack(spacing: 0) {
                ForEach(Array(members.enumerated()), id: \.element.id) { index, member in
                    HStack(spacing: Spacing.md) {
                        InitialAvatar(user: member, size: 40)
                        Text(member.name)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(Color.appOnSurface)
                        Spacer()
                        Menu {
                            ForEach(familyRelationOptions, id: \.self) { option in
                                Button(L(dynamic: option)) { picked[member.id] = option
                                    onSet(member.id, option)
                                }
                            }
                            Divider()
                            Button(L("None"), role: .destructive) { picked[member.id] = ""
                                onSet(member.id, "")
                            }
                        } label: {
                            let val = picked[member.id] ?? ""
                            HStack(spacing: 4) {
                                Text(val.isEmpty ? L("Set") : L(dynamic: val))
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(val.isEmpty ? Color.appPrimary : Color.appOnSurface)
                                Image(systemName: "chevron.up.chevron.down")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(Color.appCaption)
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.lg)
                    .padding(.vertical, 12)
                    if index < members.count - 1 {
                        Divider().padding(.leading, 68)
                    }
                }
            }
            .glassCard(cornerRadius: Radius.row)
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
        .onAppear { picked = relations }
    }
}
