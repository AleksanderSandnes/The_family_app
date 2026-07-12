@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.R
import com.sandnes.familyapp.ui.components.BirthdayPickerField
import com.sandnes.familyapp.ui.components.ErrorBanner
import com.sandnes.familyapp.ui.components.FamilyTextField
import com.sandnes.familyapp.ui.components.PrimaryButton
import com.sandnes.familyapp.ui.components.SecondaryButton
import com.sandnes.familyapp.ui.theme.Amber500
import com.sandnes.familyapp.ui.theme.AmbientBackground
import com.sandnes.familyapp.ui.theme.BrandGradient
import com.sandnes.familyapp.ui.theme.Emerald500
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.glassCard

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReset: () -> Unit,
    onNeedsVerification: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) { if (state.success) onAuthenticated() }
    LaunchedEffect(state.needsVerificationEmail) {
        state.needsVerificationEmail?.let {
            viewModel.clearNeedsVerification()
            onNeedsVerification(it)
        }
    }

    AuthScaffold(
        title = stringResource(R.string.welcome_back),
        subtitle = stringResource(R.string.sign_in_to_keep_your_family_in_sync),
    ) {
        val emailFieldDescription = stringResource(R.string.email_address_field)
        val passwordFieldDescription = stringResource(R.string.password_field)
        val signInButtonDescription = stringResource(R.string.sign_in_button)
        val googleButtonDescription = stringResource(R.string.continue_with_google_button)
        ErrorBanner(state.error?.let { stringResource(it) })
        FamilyTextField(
            value = email,
            onValueChange = {
                email = it
                viewModel.clearError()
            },
            label = stringResource(R.string.email),
            leadingIcon = Icons.Outlined.Mail,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !state.loading,
            modifier = Modifier.semantics { contentDescription = emailFieldDescription },
        )
        FamilyTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearError()
            },
            label = stringResource(R.string.password),
            leadingIcon = Icons.Outlined.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (email.isNotBlank() && password.isNotBlank() && !state.loading) {
                            viewModel.login(email, password)
                        }
                    },
                ),
            enabled = !state.loading,
            modifier = Modifier.semantics { contentDescription = passwordFieldDescription },
        )
        Text(
            stringResource(R.string.forgot_password),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(Radius.extraSmall))
                    .clickable {
                        viewModel.clearError()
                        onNavigateToReset()
                    }
                    .padding(Spacing.xs),
        )
        PrimaryButton(
            text = stringResource(R.string.sign_in),
            onClick = { viewModel.login(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank(),
            loading = state.loading,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = signInButtonDescription },
        )
        SecondaryButton(
            text = stringResource(R.string.continue_with_google),
            onClick = { viewModel.signInWithGoogle() },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = googleButtonDescription },
        )
        AuthFooter(
            prompt = stringResource(R.string.new_to_the_family_app),
            action = stringResource(R.string.create_account),
            onClick = onNavigateToRegister,
        )
    }
}

@Composable
fun RegisterScreen(
    onAuthenticated: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNeedsVerification: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(1) }

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) { if (state.success) onAuthenticated() }
    LaunchedEffect(state.needsVerificationEmail) {
        state.needsVerificationEmail?.let {
            viewModel.clearNeedsVerification()
            onNeedsVerification(it)
        }
    }
    BackHandler(enabled = step == 2) {
        step = 1
        viewModel.clearError()
    }

    val (title, subtitle) =
        when (step) {
            1 ->
                stringResource(R.string.create_your_account) to
                    stringResource(R.string.start_with_your_login_details)
            else ->
                stringResource(R.string.about_you) to
                    stringResource(R.string.optional_details_to_help_your_family_recognize_you)
        }
    AuthScaffold(title = title, subtitle = subtitle) {
        StepIndicator(currentStep = step, totalSteps = 2)
        ErrorBanner(state.error?.let { stringResource(it) })
        when (step) {
            1 ->
                RegistrationStep1(
                    name = name,
                    onNameChange = {
                        name = it
                        viewModel.clearError()
                    },
                    email = email,
                    onEmailChange = {
                        email = it
                        viewModel.clearError()
                    },
                    password = password,
                    onPasswordChange = {
                        password = it
                        viewModel.clearError()
                    },
                    confirm = confirm,
                    onConfirmChange = {
                        confirm = it
                        viewModel.clearError()
                    },
                    loading = state.loading,
                    onGoogle = { viewModel.signInWithGoogle() },
                    onNext = {
                        when {
                            name.isBlank() -> viewModel.setError(R.string.please_enter_your_name)
                            !email.contains('@') || !email.contains('.') -> viewModel.setError(R.string.please_enter_a_valid_email_address)
                            password.length < 6 -> viewModel.setError(R.string.password_must_be_at_least_6_characters)
                            password != confirm -> viewModel.setError(R.string.passwords_do_not_match)
                            else -> {
                                viewModel.clearError()
                                step = 2
                            }
                        }
                    },
                )
            else ->
                RegistrationStep2(
                    birthday = birthday,
                    onBirthdayChange = { birthday = it },
                    mobile = mobile,
                    onMobileChange = { mobile = it },
                    loading = state.loading,
                    onBack = {
                        step = 1
                        viewModel.clearError()
                    },
                    onSubmit = { viewModel.register(RegistrationForm(name, email, password, confirm, birthday, mobile)) },
                )
        }
        if (step == 1) {
            AuthFooter(
                prompt = stringResource(R.string.already_have_an_account),
                action = stringResource(R.string.sign_in),
                onClick = onNavigateToLogin,
            )
        }
    }
}

@Composable
fun ResetPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val reset by viewModel.resetState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { viewModel.clearResetFlow() } }

    val subtitle =
        when (reset.step) {
            1 -> stringResource(R.string.reset_password_subtitle)
            else -> stringResource(R.string.enter_code_and_new_password, reset.email)
        }
    AuthScaffold(title = stringResource(R.string.reset_password), subtitle = subtitle) {
        StepIndicator(currentStep = reset.step, totalSteps = 2)
        ErrorBanner(reset.error?.let { stringResource(it) })
        if (reset.step == 1) {
            FamilyTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.email),
                leadingIcon = Icons.Outlined.Mail,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (email.isNotBlank() && !reset.loading) viewModel.sendResetCode(email)
                        },
                    ),
                enabled = !reset.loading,
            )
            PrimaryButton(
                text = stringResource(R.string.send_code),
                onClick = { viewModel.sendResetCode(email) },
                enabled = email.isNotBlank(),
                loading = reset.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FamilyTextField(
                value = code,
                onValueChange = { code = it },
                label = stringResource(R.string.reset_code),
                leadingIcon = Icons.Outlined.Lock,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                enabled = !reset.loading,
            )
            FamilyTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = stringResource(R.string.new_password),
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (code.isNotBlank() && newPassword.isNotBlank() && !reset.loading) {
                                viewModel.confirmPasswordReset(code, newPassword)
                            }
                        },
                    ),
                enabled = !reset.loading,
            )
            PasswordStrengthBar(newPassword)
            PrimaryButton(
                text = stringResource(R.string.set_new_password),
                onClick = { viewModel.confirmPasswordReset(code, newPassword) },
                enabled = code.isNotBlank() && newPassword.isNotBlank(),
                loading = reset.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (reset.resendCooldownSeconds > 0) {
                    stringResource(R.string.resend_code_in_seconds, reset.resendCooldownSeconds)
                } else {
                    stringResource(R.string.resend_code)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color =
                    if (reset.resendCooldownSeconds > 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(Radius.extraSmall))
                        .clickable(enabled = reset.resendCooldownSeconds == 0 && !reset.loading) {
                            viewModel.resendResetCode()
                        }
                        .padding(Spacing.xs),
            )
        }
        AuthFooter(
            prompt = stringResource(R.string.remembered_your_password),
            action = stringResource(R.string.sign_in),
            onClick = onBackToLogin,
        )
    }
}

@Composable
fun VerifyEmailScreen(
    email: String,
    sendCode: Boolean,
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val verify by viewModel.verifyState.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.startEmailVerification(email, sendCode) }
    DisposableEffect(Unit) { onDispose { viewModel.clearVerifyFlow() } }

    AuthScaffold(
        title = stringResource(R.string.verify_your_email),
        subtitle = stringResource(R.string.enter_the_code_we_emailed_to, email),
    ) {
        ErrorBanner(verify.error?.let { stringResource(it) })
        FamilyTextField(
            value = code,
            onValueChange = { code = it },
            label = stringResource(R.string.reset_code),
            leadingIcon = Icons.Outlined.Lock,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (code.isNotBlank() && !verify.loading) viewModel.confirmSignupEmail(code)
                    },
                ),
            enabled = !verify.loading,
        )
        PrimaryButton(
            text = stringResource(R.string.verify_code),
            onClick = { viewModel.confirmSignupEmail(code) },
            enabled = code.isNotBlank(),
            loading = verify.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (verify.resendCooldownSeconds > 0) {
                stringResource(R.string.resend_code_in_seconds, verify.resendCooldownSeconds)
            } else {
                stringResource(R.string.resend_code)
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color =
                if (verify.resendCooldownSeconds > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(Radius.extraSmall))
                    .clickable(enabled = verify.resendCooldownSeconds == 0 && !verify.loading) {
                        viewModel.resendSignupCode()
                    }
                    .padding(Spacing.xs),
        )
        AuthFooter(
            prompt = stringResource(R.string.already_have_an_account),
            action = stringResource(R.string.sign_in),
            onClick = onBackToLogin,
        )
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    // Registration is a 2-step flow; the default matches so a missed argument can't lie.
    totalSteps: Int = 2,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 1..totalSteps) {
                val isActive = i == currentStep
                val isCompleted = i < currentStep
                Box(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .border(
                                width = if (isActive || isCompleted) 0.dp else 1.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                shape = CircleShape,
                            ).clip(CircleShape)
                            .background(
                                if (isActive || isCompleted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        i.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (isActive || isCompleted) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                if (i < totalSteps) {
                    Box(
                        modifier =
                            Modifier
                                .width(44.dp)
                                .height(2.dp)
                                .background(
                                    if (i < currentStep) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    },
                                ),
                    )
                }
            }
        }
    }
}

// passwordStrength(...) lives in AuthViewModel.kt (internal, pure, unit-tested).

@Composable
private fun PasswordStrengthBar(password: String) {
    if (password.isEmpty()) return
    val strength = passwordStrength(password)
    val label =
        stringResource(
            when (strength) {
                0 -> R.string.too_short
                1 -> R.string.weak
                2 -> R.string.medium
                3 -> R.string.strong
                else -> R.string.strong
            },
        )
    val color =
        when (strength) {
            0, 1 -> MaterialTheme.colorScheme.error
            2 -> Amber500
            else -> Emerald500
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 1..3) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= strength) color else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun RegistrationStep1(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirm: String,
    onConfirmChange: (String) -> Unit,
    loading: Boolean,
    onNext: () -> Unit,
    onGoogle: () -> Unit,
) {
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    val nameFieldDescription = stringResource(R.string.full_name_field)
    val emailFieldDescription = stringResource(R.string.email_address_field)
    val passwordFieldDescription = stringResource(R.string.password_field)
    val confirmFieldDescription = stringResource(R.string.confirm_password_field)
    val continueButtonDescription = stringResource(R.string.continue_next_step_button)
    val googleButtonDescription = stringResource(R.string.continue_with_google_button)

    FamilyTextField(
        value = name,
        onValueChange = onNameChange,
        label = stringResource(R.string.full_name),
        leadingIcon = Icons.Outlined.Person,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
        enabled = !loading,
        modifier = Modifier.semantics { contentDescription = nameFieldDescription },
    )
    FamilyTextField(
        value = email,
        onValueChange = onEmailChange,
        label = stringResource(R.string.email),
        leadingIcon = Icons.Outlined.Mail,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
        enabled = !loading,
        modifier =
            Modifier
                .focusRequester(emailFocus)
                .semantics { contentDescription = emailFieldDescription },
    )
    FamilyTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.password),
        leadingIcon = Icons.Outlined.Lock,
        isPassword = true,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocus() }),
        enabled = !loading,
        modifier =
            Modifier
                .focusRequester(passwordFocus)
                .semantics { contentDescription = passwordFieldDescription },
    )
    PasswordStrengthBar(password)
    FamilyTextField(
        value = confirm,
        onValueChange = onConfirmChange,
        label = stringResource(R.string.confirm_password),
        leadingIcon = Icons.Outlined.Lock,
        isPassword = true,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { onNext() }),
        enabled = !loading,
        modifier =
            Modifier
                .focusRequester(confirmFocus)
                .semantics { contentDescription = confirmFieldDescription },
    )
    PrimaryButton(
        text = stringResource(R.string.continue_label),
        onClick = onNext,
        enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && confirm.isNotBlank(),
        loading = loading,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = continueButtonDescription },
    )
    SecondaryButton(
        text = stringResource(R.string.continue_with_google),
        onClick = onGoogle,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = googleButtonDescription },
    )
}

@Composable
private fun RegistrationStep2(
    birthday: String,
    onBirthdayChange: (String) -> Unit,
    mobile: String,
    onMobileChange: (String) -> Unit,
    loading: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BirthdayPickerField(
            value = birthday,
            onChange = onBirthdayChange,
        )
        Text(
            text = stringResource(R.string.used_to_track_family_birthdays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    val mobileFieldDescription = stringResource(R.string.mobile_phone_number_field_optional)
    val createAccountButtonDescription = stringResource(R.string.create_account_button)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FamilyTextField(
            value = mobile,
            onValueChange = onMobileChange,
            label = stringResource(R.string.mobile_optional),
            leadingIcon = Icons.Outlined.Phone,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            enabled = !loading,
            modifier = Modifier.semantics { contentDescription = mobileFieldDescription },
        )
        Text(
            text = stringResource(R.string.for_family_contact_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    PrimaryButton(
        text = stringResource(R.string.create_account),
        onClick = onSubmit,
        loading = loading,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = createAccountButtonDescription },
    )
    SecondaryButton(
        text = stringResource(R.string.back),
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Glass House front door: the ambient wash is the canvas; the brand gradient appears
    // only on the small identity badge (One Gradient Rule), and the form sits on glass.
    AmbientBackground {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Brand header — gradient identity badge over the wash.
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Diversity3,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.the_family_app),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.one_home_for_everything_you_share),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))

            // Animated form card entrance — a glass surface over the wash.
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glassCard(Radius.sheet)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun AuthFooter(
    prompt: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            action,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radius.extraSmall))
                    .clickable(onClick = onClick)
                    .padding(Spacing.xs),
        )
    }
}
