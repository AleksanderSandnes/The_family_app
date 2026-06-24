package com.example.mainactivity.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.ui.components.BirthdayPickerField
import com.example.mainactivity.ui.components.ErrorBanner
import com.example.mainactivity.ui.components.FamilyTextField
import com.example.mainactivity.ui.components.PrimaryButton
import com.example.mainactivity.ui.components.SecondaryButton
import com.example.mainactivity.ui.theme.heroGradient

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) { if (state.success) onAuthenticated() }

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Sign in to keep your family in sync."
    ) {
        ErrorBanner(state.error)
        FamilyTextField(email, { email = it; viewModel.clearError() }, "Email", leadingIcon = Icons.Outlined.Mail, keyboardType = KeyboardType.Email)
        FamilyTextField(password, { password = it; viewModel.clearError() }, "Password", leadingIcon = Icons.Outlined.Lock, isPassword = true)
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = "Sign in",
            onClick = { viewModel.login(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank(),
            loading = state.loading,
            modifier = Modifier.fillMaxWidth()
        )
        AuthFooter(
            prompt = "New to The Family App?",
            action = "Create account",
            onClick = onNavigateToRegister
        )
    }
}

@Composable
fun RegisterScreen(
    onAuthenticated: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
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
    BackHandler(enabled = step == 2) { step = 1; viewModel.clearError() }

    val (title, subtitle) = when (step) {
        1 -> "Create your account" to "Start with your login details."
        else -> "About you" to "Optional details to help your family recognize you."
    }

    AuthScaffold(title = title, subtitle = subtitle) {
        StepIndicator(currentStep = step, totalSteps = 2)
        Spacer(Modifier.height(4.dp))
        ErrorBanner(state.error)
        when (step) {
            1 -> RegistrationStep1(
                name = name, onNameChange = { name = it; viewModel.clearError() },
                email = email, onEmailChange = { email = it; viewModel.clearError() },
                password = password, onPasswordChange = { password = it; viewModel.clearError() },
                confirm = confirm, onConfirmChange = { confirm = it; viewModel.clearError() },
                onNext = {
                    when {
                        name.isBlank() -> viewModel.setError("Please enter your name.")
                        !email.contains('@') || !email.contains('.') -> viewModel.setError("Please enter a valid email address.")
                        password.length < 6 -> viewModel.setError("Password must be at least 6 characters.")
                        password != confirm -> viewModel.setError("Passwords do not match.")
                        else -> { viewModel.clearError(); step = 2 }
                    }
                }
            )
            else -> RegistrationStep2(
                birthday = birthday, onBirthdayChange = { birthday = it },
                mobile = mobile, onMobileChange = { mobile = it },
                loading = state.loading,
                onBack = { step = 1; viewModel.clearError() },
                onSubmit = { viewModel.register(name, email, password, confirm, birthday, mobile) }
            )
        }
        if (step == 1) {
            AuthFooter(
                prompt = "Already have an account?",
                action = "Sign in",
                onClick = onNavigateToLogin
            )
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int = 3) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..totalSteps) {
            val isActive = i == currentStep
            val isCompleted = i < currentStep
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive || isCompleted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    i.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive || isCompleted) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (i < totalSteps) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(2.dp)
                        .background(
                            if (i < currentStep) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun RegistrationStep1(
    name: String, onNameChange: (String) -> Unit,
    email: String, onEmailChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    confirm: String, onConfirmChange: (String) -> Unit,
    onNext: () -> Unit
) {
    FamilyTextField(name, onNameChange, "Full name", leadingIcon = Icons.Outlined.Person)
    FamilyTextField(email, onEmailChange, "Email", leadingIcon = Icons.Outlined.Mail, keyboardType = KeyboardType.Email)
    FamilyTextField(password, onPasswordChange, "Password", leadingIcon = Icons.Outlined.Lock, isPassword = true)
    FamilyTextField(confirm, onConfirmChange, "Confirm password", leadingIcon = Icons.Outlined.Lock, isPassword = true)
    Spacer(Modifier.height(4.dp))
    PrimaryButton(
        text = "Continue",
        onClick = onNext,
        enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && confirm.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RegistrationStep2(
    birthday: String, onBirthdayChange: (String) -> Unit,
    mobile: String, onMobileChange: (String) -> Unit,
    loading: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    BirthdayPickerField(value = birthday, onChange = onBirthdayChange)
    FamilyTextField(mobile, onMobileChange, "Mobile (optional)", leadingIcon = Icons.Outlined.Phone, keyboardType = KeyboardType.Phone)
    Spacer(Modifier.height(4.dp))
    PrimaryButton(
        text = "Create account",
        onClick = onSubmit,
        loading = loading,
        modifier = Modifier.fillMaxWidth()
    )
    SecondaryButton(
        text = "Back",
        onClick = onBack,
        modifier = Modifier.fillMaxWidth()
    )
}


@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    Box(
        Modifier
            .fillMaxSize()
            .background(heroGradient(dark)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Brand header
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Diversity3,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "The Family App",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "One home for everything you share",
                color = Color.White.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))

            // Form card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                tonalElevation = 2.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun AuthFooter(prompt: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) {
            Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
