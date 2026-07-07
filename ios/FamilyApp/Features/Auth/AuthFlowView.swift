// Auth flow — the iOS twin of AuthScreens.kt: hero-gradient scaffold with a floating
// form card, login screen, 2-step registration with step indicator and password
// strength bar. Success flips the root auth gate via SessionStore.
import SwiftUI

struct AuthFlowView: View {
    @State private var viewModel = AuthViewModel()
    @State private var showRegister = false

    var body: some View {
        NavigationStack {
            LoginScreen(viewModel: viewModel) { showRegister = true }
                .navigationDestination(isPresented: $showRegister) {
                    RegisterScreen(viewModel: viewModel)
                        .navigationBarBackButtonHidden()
                }
        }
    }
}

// MARK: - Login

struct LoginScreen: View {
    @Bindable var viewModel: AuthViewModel
    let onNavigateToRegister: () -> Void

    @State private var email = ""
    @State private var password = ""
    @State private var showForgotDialog = false

    var body: some View {
        AuthScaffold(title: L("Welcome back"), subtitle: L("Sign in to keep your family in sync.")) {
            ErrorBanner(message: viewModel.error)
            FamilyTextField(
                label: L("Email"),
                text: $email.clearingError(viewModel),
                systemImage: "envelope",
                keyboardType: .emailAddress,
                textContentType: .emailAddress,
                autocapitalization: .never,
                whiteField: true
            )
            .accessibilityLabel("Email address field")
            FamilyTextField(
                label: L("Password"),
                text: $password.clearingError(viewModel),
                systemImage: "lock",
                isPassword: true,
                textContentType: .password,
                whiteField: true
            )
            .accessibilityLabel("Password field")

            Button("Forgot password?") { showForgotDialog = true }
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.appPrimary)
                .frame(maxWidth: .infinity, alignment: .trailing)

            PrimaryButton(
                text: L("Sign in"),
                enabled: !email.isEmpty && !password.isEmpty,
                loading: viewModel.loading
            ) {
                viewModel.login(email: email, password: password)
            }
            .accessibilityLabel("Sign in button")

            GlassButton(text: L("Continue with Google"), systemImage: "globe", whiter: true) {
                viewModel.signInWithGoogle()
            }
            .accessibilityLabel("Continue with Google button")

            AuthFooter(prompt: L("New to The Family App?"), action: L("Create account")) {
                viewModel.clearError()
                onNavigateToRegister()
            }
        }
        .alert("Forgot password?", isPresented: $showForgotDialog) {
            Button("OK") {}
        } message: {
            Text("Password reset is coming soon. Contact support at support@familyapp.com")
        }
    }
}

// MARK: - Register (2 steps)

struct RegisterScreen: View {
    @Bindable var viewModel: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var step = 1
    @State private var form = RegistrationForm()

    private var title: String {
        step == 1 ? L("Create your account") : L("About you")
    }

    private var subtitle: String {
        step == 1
            ? L("Start with your login details.")
            : L("Optional details to help your family recognize you.")
    }

    var body: some View {
        AuthScaffold(title: title, subtitle: subtitle, showIcon: false) {
            StepIndicator(currentStep: step, totalSteps: 2)
            ErrorBanner(message: viewModel.error)
            if step == 1 {
                registrationStep1
                AuthFooter(prompt: L("Already have an account?"), action: L("Sign in")) {
                    viewModel.clearError()
                    dismiss()
                }
            } else {
                registrationStep2
            }
        }
    }

    @ViewBuilder private var registrationStep1: some View {
        FamilyTextField(
            label: L("Full name"),
            text: $form.name.clearingError(viewModel),
            systemImage: "person",
            textContentType: .name,
            autocapitalization: .words,
            whiteField: true
        )
        FamilyTextField(
            label: L("Email"),
            text: $form.email.clearingError(viewModel),
            systemImage: "envelope",
            keyboardType: .emailAddress,
            textContentType: .emailAddress,
            autocapitalization: .never,
            whiteField: true
        )
        FamilyTextField(
            label: L("Password"),
            text: $form.password.clearingError(viewModel),
            systemImage: "lock",
            isPassword: true,
            textContentType: .newPassword,
            whiteField: true
        )
        PasswordStrengthBar(password: form.password)
        FamilyTextField(
            label: L("Confirm password"),
            text: $form.confirm.clearingError(viewModel),
            systemImage: "lock",
            isPassword: true,
            textContentType: .newPassword,
            whiteField: true
        )
        PrimaryButton(
            text: L("Continue"),
            enabled: !form.name.isEmpty && !form.email.isEmpty
                && !form.password.isEmpty && !form.confirm.isEmpty,
            loading: viewModel.loading
        ) {
            advanceToStep2()
        }
        .accessibilityLabel("Continue to next step button")

        GlassButton(text: L("Continue with Google"), systemImage: "globe", whiter: true) {
            viewModel.signInWithGoogle()
        }
        .accessibilityLabel("Continue with Google button")
    }

    @ViewBuilder private var registrationStep2: some View {
        VStack(alignment: .leading, spacing: 2) {
            BirthdayPickerField(isoDate: $form.birthday, label: L("Birthday (optional)"))
            Text("Used to track family birthdays")
                .font(.labelMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
                .padding(.leading, Spacing.lg)
        }
        VStack(alignment: .leading, spacing: 2) {
            FamilyTextField(
                label: L("Mobile (optional)"),
                text: $form.mobile,
                systemImage: "phone",
                keyboardType: .phonePad,
                textContentType: .telephoneNumber,
                whiteField: true
            )
            Text("For family contact info")
                .font(.labelMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
                .padding(.leading, Spacing.lg)
        }
        PrimaryButton(text: L("Create account"), loading: viewModel.loading) {
            viewModel.register(form)
        }
        .accessibilityLabel("Create account button")
        SecondaryButton(text: L("Back")) {
            step = 1
            viewModel.clearError()
        }
    }

    private func advanceToStep2() {
        if form.name.trimmingCharacters(in: .whitespaces).isEmpty {
            viewModel.setError("Please enter your name.")
        } else if !form.email.contains("@") || !form.email.contains(".") {
            viewModel.setError("Please enter a valid email address.")
        } else if form.password.count < minPasswordLength {
            viewModel.setError("Password must be at least 6 characters.")
        } else if form.password != form.confirm {
            viewModel.setError("Passwords do not match.")
        } else {
            viewModel.clearError()
            step = 2
        }
    }
}

// MARK: - Building blocks

/// Hero-gradient full-screen background with brand header and a floating form card.
struct AuthScaffold<Content: View>: View {
    let title: String
    let subtitle: String
    var showIcon = true
    @ViewBuilder let content: () -> Content

    @Environment(\.colorScheme) private var colorScheme
    @State private var visible = false

    var body: some View {
        ZStack {
            // Full-identity hero gradient (linear-gradient(160deg,#5457E8,#6D3AE0,#7C3AED))
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x5457E8), location: 0),
                    .init(color: Color(hex: 0x6D3AE0), location: 0.55),
                    .init(color: Color(hex: 0x7C3AED), location: 1),
                ],
                startPoint: UnitPoint(x: 0.15, y: 0),
                endPoint: UnitPoint(x: 0.85, y: 1)
            )
            .ignoresSafeArea()
            // Faint bokeh
            .overlay(alignment: .topTrailing) {
                Circle().fill(.white.opacity(0.09)).frame(width: 260, height: 260)
                    .offset(x: 90, y: -60).blur(radius: 4)
            }
            .overlay(alignment: .bottomLeading) {
                Circle().fill(.white.opacity(0.06)).frame(width: 220, height: 220)
                    .offset(x: -70, y: 40).blur(radius: 4)
            }

            ScrollView {
                VStack(spacing: 0) {
                    // Glass app-icon tile
                    if showIcon {
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(Color.white.opacity(0.2))
                            .frame(width: 70, height: 70)
                            .overlay(
                                RoundedRectangle(cornerRadius: 22, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.4), lineWidth: 1)
                            )
                            .overlay(
                                Image(systemName: "person.3.fill")
                                    .font(.system(size: 30, weight: .medium))
                                    .foregroundStyle(.white)
                            )
                            .shadow(color: .black.opacity(0.15), radius: 12, y: 6)
                    }
                    Text("The Family App")
                        .font(.system(size: 23, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.top, 16)
                    Text("One home for everything you share")
                        .font(.system(size: 13.5))
                        .foregroundStyle(.white.opacity(0.8))
                        .padding(.top, 2)

                    // Floating frosted glass form card
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text(title)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(Color.appOnSurface)
                        Text(subtitle)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.appOnSurfaceVariant)
                        content()
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Spacing.xxl)
                    .padding(.vertical, Spacing.xxxl)
                    .background(
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .fill(Color(light: Color(hex: 0xF7F6FC), dark: Palette.inkSurface))
                    )
                    .shadow(color: Color(hex: 0x0A0C28).opacity(0.3), radius: 30, y: 20)
                    .padding(.top, Spacing.xxxl)
                    .opacity(visible ? 1 : 0)
                    .offset(y: visible ? 0 : 24)
                }
                .frame(maxWidth: 480)
                .padding(.horizontal, Spacing.xl)
                .padding(.vertical, 48)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.4)) { visible = true }
        }
    }
}

struct StepIndicator: View {
    let currentStep: Int
    var totalSteps = 2

    var body: some View {
        HStack(spacing: 0) {
            ForEach(1...totalSteps, id: \.self) { index in
                let reached = index <= currentStep
                ZStack {
                    Circle()
                        .fill(reached ? Color.appPrimary : .clear)
                        .frame(width: 30, height: 30)
                    if !reached {
                        Circle()
                            .strokeBorder(Color.appOnSurface.opacity(0.45), lineWidth: 1.5)
                            .frame(width: 30, height: 30)
                    }
                    Text("\(index)")
                        .font(.labelMedium.weight(.bold))
                        .foregroundStyle(reached ? Color.appOnPrimary : Color.appOnSurfaceVariant)
                }
                if index < totalSteps {
                    Rectangle()
                        .fill(index < currentStep
                            ? Color.appPrimary
                            : Color.appOnSurface.opacity(0.35))
                        .frame(width: 44, height: 2)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct PasswordStrengthBar: View {
    let password: String

    var body: some View {
        if !password.isEmpty {
            let strength = passwordStrength(password)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                HStack(spacing: Spacing.xs) {
                    ForEach(1...3, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 2)
                            .fill(index <= strength ? color(for: strength) : Color.appSurfaceVariant)
                            .frame(height: 4)
                    }
                }
                Text(LocalizedStringKey(label(for: strength)))
                    .font(.labelMedium)
                    .foregroundStyle(color(for: strength))
            }
        }
    }

    private func label(for strength: Int) -> String {
        switch strength {
        case 0: "Too short"
        case 1: "Weak"
        case 2: "Medium"
        default: "Strong"
        }
    }

    private func color(for strength: Int) -> Color {
        switch strength {
        case 0, 1: .appError
        case 2: .appWarning
        default: .appSuccess
        }
    }
}

/// Read-only field that opens a date picker sheet; stores ISO-8601 (yyyy-MM-dd) —
/// mirrors BirthdayPickerField in Components.kt.
struct BirthdayPickerField: View {
    @Binding var isoDate: String
    var label = "Birthday (optional)"

    @State private var showPicker = false
    @State private var selection = Date()

    private static let isoFormat: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()

    var body: some View {
        Button {
            let fallback = Calendar.current.date(byAdding: .year, value: -30, to: .now) ?? .now
            selection = Self.isoFormat.date(from: isoDate) ?? fallback
            showPicker = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "birthday.cake")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.appPrimary)
                    .frame(width: 22)
                Text(displayText.isEmpty ? label : displayText)
                    .font(.system(size: 16))
                    .foregroundStyle(displayText.isEmpty ? Color.appCaption : Color.appOnSurface)
                Spacer()
                Image(systemName: "chevron.down")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(.horizontal, 16)
            .frame(height: 54)
            .glassCard(cornerRadius: Radius.field)
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showPicker) {
            VStack(spacing: Spacing.lg) {
                DatePicker("Birthday", selection: $selection, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding(Spacing.lg)
                PrimaryButton(text: L("Done")) {
                    isoDate = Self.isoFormat.string(from: selection)
                    showPicker = false
                }
                .padding(.horizontal, Spacing.screenEdge)
            }
            .padding(.bottom, Spacing.lg)
            .presentationDetents([.medium, .large])
            .presentationCornerRadius(Radius.sheet)
        }
    }

    private var displayText: String {
        guard let date = Self.isoFormat.date(from: isoDate) else { return "" }
        return date.formatted(.dateTime.month(.wide).day().year().locale(appLocale))
    }
}

struct AuthFooter: View {
    let prompt: String
    let action: String
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: Spacing.xs) {
            Text(prompt)
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
            Button(action: onTap) {
                Text(action)
                    .font(.bodyMedium.weight(.semibold))
                    .foregroundStyle(Color.appPrimary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xs)
    }
}

// MARK: - Helpers

extension Binding where Value == String {
    /// Clears the view model error on every edit — mirrors the Android onValueChange
    /// pattern of `viewModel.clearError()` alongside the state update.
    @MainActor
    fileprivate func clearingError(_ viewModel: AuthViewModel) -> Binding<String> {
        Binding(
            get: { wrappedValue },
            set: { newValue in
                wrappedValue = newValue
                viewModel.clearError()
            }
        )
    }
}
