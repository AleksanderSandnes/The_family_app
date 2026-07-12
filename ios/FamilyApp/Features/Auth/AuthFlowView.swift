// Auth flow: hero-gradient scaffold with a floating form card, login screen, 2-step
// registration with step indicator and password strength bar. Success flips the root
// auth gate via SessionStore.
import SwiftUI

struct AuthFlowView: View {
    @State private var viewModel = AuthViewModel()
    @State private var showRegister = false
    @State private var showReset = false

    var body: some View {
        NavigationStack {
            LoginScreen(
                viewModel: viewModel,
                onNavigateToRegister: { showRegister = true },
                onNavigateToReset: { showReset = true }
            )
            .navigationDestination(isPresented: $showRegister) {
                RegisterScreen(viewModel: viewModel)
                    .navigationBarBackButtonHidden()
            }
            .navigationDestination(isPresented: $showReset) {
                ResetPasswordScreen(viewModel: viewModel)
                    .navigationBarBackButtonHidden()
            }
        }
    }
}

// MARK: - Login

struct LoginScreen: View {
    @Bindable var viewModel: AuthViewModel
    let onNavigateToRegister: () -> Void
    let onNavigateToReset: () -> Void

    @State private var email = ""
    @State private var password = ""

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

            Button(L("Forgot password?")) {
                viewModel.clearError()
                onNavigateToReset()
            }
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
    }
}

// MARK: - Reset password (2 steps)

struct ResetPasswordScreen: View {
    @Bindable var viewModel: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var email = ""
    @State private var code = ""
    @State private var newPassword = ""

    var body: some View {
        AuthScaffold(
            title: L("Reset password"),
            subtitle: viewModel.resetStep == 1
                ? L("We'll email you a 6-digit code to reset your password.")
                : L("If an account exists for \(viewModel.resetEmail), we've emailed a 6-digit code. Enter it below with your new password."),
            showIcon: false
        ) {
            StepIndicator(currentStep: viewModel.resetStep, totalSteps: 2)
            ErrorBanner(message: viewModel.error)
            if viewModel.resetStep == 1 {
                FamilyTextField(
                    label: L("Email"),
                    text: $email.clearingError(viewModel),
                    systemImage: "envelope",
                    keyboardType: .emailAddress,
                    textContentType: .emailAddress,
                    autocapitalization: .never,
                    whiteField: true
                )
                PrimaryButton(
                    text: L("Send code"),
                    enabled: !email.isEmpty,
                    loading: viewModel.loading
                ) {
                    viewModel.sendResetCode(email: email)
                }
            } else {
                FamilyTextField(
                    label: L("6-digit code"),
                    text: $code.clearingError(viewModel),
                    systemImage: "key",
                    keyboardType: .numberPad,
                    whiteField: true
                )
                FamilyTextField(
                    label: L("New password"),
                    text: $newPassword.clearingError(viewModel),
                    systemImage: "lock",
                    isPassword: true,
                    textContentType: .newPassword,
                    whiteField: true
                )
                PasswordStrengthBar(password: newPassword)
                PrimaryButton(
                    text: L("Set new password"),
                    enabled: !code.isEmpty && !newPassword.isEmpty,
                    loading: viewModel.loading
                ) {
                    viewModel.confirmPasswordReset(code: code, newPassword: newPassword)
                }
                Button(
                    viewModel.resetCooldown > 0
                        ? L("Resend code (\(viewModel.resetCooldown) s)")
                        : L("Resend code")
                ) {
                    viewModel.resendResetCode()
                }
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(viewModel.resetCooldown > 0 ? Color.secondary : Color.appPrimary)
                .disabled(viewModel.resetCooldown > 0 || viewModel.loading)
                .frame(maxWidth: .infinity)
            }
            AuthFooter(prompt: L("Remembered your password?"), action: L("Sign in")) {
                viewModel.clearResetFlow()
                dismiss()
            }
        }
        .onDisappear { viewModel.clearResetFlow() }
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

    @State private var visible = false

    var body: some View {
        // Glass House front door: the ambient wash is the canvas; the brand gradient appears
        // only on the small identity badge (One Gradient Rule), and the form sits on glass.
        // Mirrors Android AuthScaffold.
        ScrollView {
            VStack(spacing: 0) {
                // Brand identity badge over the wash
                if showIcon {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Gradients.brand)
                        .frame(width: 70, height: 70)
                        .overlay(
                            Image(systemName: "person.3.fill")
                                .font(.system(size: 30, weight: .medium))
                                .foregroundStyle(.white)
                        )
                        .shadow(color: Palette.shadowInk.opacity(0.15), radius: 12, y: 6)
                }
                Text(L("The Family App"))
                    .font(.system(size: 23, weight: .bold))
                    .foregroundStyle(Color.appOnSurface)
                    .padding(.top, 16)
                Text("One home for everything you share")
                    .font(.system(size: 13.5))
                    .foregroundStyle(Color.appOnSurfaceVariant)
                    .padding(.top, 2)

                // Form card — a glass surface over the wash
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
                .glassCard(cornerRadius: Radius.sheet)
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
        .ambientBackground()
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

/// Read-only field that opens a date picker sheet; stores ISO-8601 (yyyy-MM-dd).
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
    /// Clears the view model error on every edit.
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
