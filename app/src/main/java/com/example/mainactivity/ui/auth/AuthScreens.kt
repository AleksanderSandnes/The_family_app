@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mainactivity.ui.components.BirthdayPickerField
import com.example.mainactivity.ui.components.ErrorBanner
import com.example.mainactivity.ui.components.FamilyTextField
import com.example.mainactivity.ui.components.PrimaryButton
import com.example.mainactivity.ui.components.SecondaryButton
import com.example.mainactivity.ui.theme.Amber500
import com.example.mainactivity.ui.theme.Emerald500
import com.example.mainactivity.ui.theme.heroGradient

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onAuthenticated() }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Forgot password?") },
            text = {
                Text(
                    "Password reset is coming soon. Contact support at support@familyapp.com",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("OK") }
            },
        )
    }

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Sign in to keep your family in sync.",
    ) {
        ErrorBanner(state.error)
        FamilyTextField(
            value = email,
            onValueChange = {
                email = it
                viewModel.clearError()
            },
            label = "Email",
            leadingIcon = Icons.Outlined.Mail,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !state.loading,
            modifier = Modifier.semantics { contentDescription = "Email address field" },
        )
        FamilyTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearError()
            },
            label = "Password",
            leadingIcon = Icons.Outlined.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = {
                    if (email.isNotBlank() && password.isNotBlank() && !state.loading) {
                        viewModel.login(email, password)
                    }
                },
            ),
            enabled = !state.loading,
            modifier = Modifier.semantics { contentDescription = "Password field" },
        )
        TextButton(
            onClick = { showForgotDialog = true },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                "Forgot password?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        PrimaryButton(
            text = "Sign in",
            onClick = { viewModel.login(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank(),
            loading = state.loading,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Sign in button" },
        )
        AuthFooter(
            prompt = "New to The Family App?",
            action = "Create account",
            onClick = onNavigateToRegister,
        )
    }
}

@Composable
fun RegisterScreen(
    onAuthenticated: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(1) }

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) { if (state.success) onAuthenticated() }
    BackHandler(enabled = step == 2) {
        step = 1
        viewModel.clearError()
    }

    val (title, subtitle) =
        when (step) {
            1 -> "Create your account" to "Start with your login details."
            else -> "About you" to "Optional details to help your family recognize you."
        }

    AuthScaffold(title = title, subtitle = subtitle) {
        StepIndicator(currentStep = step, totalSteps = 2)
        Spacer(Modifier.height(4.dp))
        ErrorBanner(state.error)
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
                    onNext = {
                        when {
                            name.isBlank() -> viewModel.setError("Please enter your name.")
                            !email.contains('@') || !email.contains('.') -> viewModel.setError("Please enter a valid email address.")
                            password.length < 6 -> viewModel.setError("Password must be at least 6 characters.")
                            password != confirm -> viewModel.setError("Passwords do not match.")
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
                    onSubmit = { viewModel.register(name, email, password, confirm, birthday, mobile) },
                )
        }
        if (step == 1) {
            AuthFooter(
                prompt = "Already have an account?",
                action = "Sign in",
                onClick = onNavigateToLogin,
            )
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int = 3,
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
                            )
                            .clip(CircleShape)
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

/** Calculates password strength score 0-3 based on length and character variety. */
private fun passwordStrength(password: String): Int {
    if (password.length < 6) return 0
    var score = 1 // at least 6 chars = minimum score of 1
    if (password.length >= 8) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score.coerceIn(0, 3)
}

@Composable
private fun PasswordStrengthBar(password: String) {
    if (password.isEmpty()) return
    val strength = passwordStrength(password)
    val label = when (strength) {
        0 -> "Too short"
        1 -> "Weak"
        2 -> "Medium"
        3 -> "Strong"
        else -> "Strong"
    }
    val color = when (strength) {
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
                    modifier = Modifier
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
) {
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    FamilyTextField(
        value = name,
        onValueChange = onNameChange,
        label = "Full name",
        leadingIcon = Icons.Outlined.Person,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
        enabled = !loading,
        modifier = Modifier.semantics { contentDescription = "Full name field" },
    )
    FamilyTextField(
        value = email,
        onValueChange = onEmailChange,
        label = "Email",
        leadingIcon = Icons.Outlined.Mail,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
        enabled = !loading,
        modifier = Modifier
            .focusRequester(emailFocus)
            .semantics { contentDescription = "Email address field" },
    )
    FamilyTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = "Password",
        leadingIcon = Icons.Outlined.Lock,
        isPassword = true,
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocus() }),
        enabled = !loading,
        modifier = Modifier
            .focusRequester(passwordFocus)
            .semantics { contentDescription = "Password field" },
    )
    PasswordStrengthBar(password)
    FamilyTextField(
        value = confirm,
        onValueChange = onConfirmChange,
        label = "Confirm password",
        leadingIcon = Icons.Outlined.Lock,
        isPassword = true,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { onNext() }),
        enabled = !loading,
        modifier = Modifier
            .focusRequester(confirmFocus)
            .semantics { contentDescription = "Confirm password field" },
    )
    Spacer(Modifier.height(4.dp))
    PrimaryButton(
        text = "Continue",
        onClick = onNext,
        enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && confirm.isNotBlank(),
        loading = loading,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Continue to next step button" },
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
            text = "Used to track family birthdays",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FamilyTextField(
            value = mobile,
            onValueChange = onMobileChange,
            label = "Mobile (optional)",
            leadingIcon = Icons.Outlined.Phone,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            enabled = !loading,
            modifier = Modifier.semantics { contentDescription = "Mobile phone number field (optional)" },
        )
        Text(
            text = "For family contact info",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    PrimaryButton(
        text = "Create account",
        onClick = onSubmit,
        loading = loading,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Create account button" },
    )
    SecondaryButton(
        text = "Back",
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
    val dark = isSystemInDarkTheme()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(heroGradient(dark)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Brand header
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
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
                "The Family App",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "One home for everything you share",
                color = Color.White.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))

            // Animated form card entrance
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
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
                        Spacer(Modifier.height(4.dp))
                        content()
                    }
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
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) {
            Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
