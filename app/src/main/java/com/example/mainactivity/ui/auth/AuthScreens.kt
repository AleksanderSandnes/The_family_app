package com.example.mainactivity.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.mainactivity.ui.components.ErrorBanner
import com.example.mainactivity.ui.components.FamilyTextField
import com.example.mainactivity.ui.components.PrimaryButton
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
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) { if (state.success) onAuthenticated() }

    AuthScaffold(
        title = "Create your account",
        subtitle = "Bring everyone together in one calm, shared space."
    ) {
        ErrorBanner(state.error)
        FamilyTextField(name, { name = it; viewModel.clearError() }, "Full name")
        FamilyTextField(email, { email = it; viewModel.clearError() }, "Email", leadingIcon = Icons.Outlined.Mail, keyboardType = KeyboardType.Email)
        FamilyTextField(mobile, { mobile = it }, "Mobile (optional)", keyboardType = KeyboardType.Phone)
        FamilyTextField(birthday, { birthday = it }, "Birthday (optional)")
        FamilyTextField(password, { password = it; viewModel.clearError() }, "Password", leadingIcon = Icons.Outlined.Lock, isPassword = true)
        FamilyTextField(confirm, { confirm = it; viewModel.clearError() }, "Confirm password", leadingIcon = Icons.Outlined.Lock, isPassword = true)
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = "Create account",
            onClick = { viewModel.register(name, email, password, confirm, birthday, mobile) },
            loading = state.loading,
            modifier = Modifier.fillMaxWidth()
        )
        AuthFooter(
            prompt = "Already have an account?",
            action = "Sign in",
            onClick = onNavigateToLogin
        )
    }
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
            .background(heroGradient(dark))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Diversity3,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("The Family App", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("One home for everything you share", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    content()
                    Spacer(Modifier.height(8.dp))
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
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
