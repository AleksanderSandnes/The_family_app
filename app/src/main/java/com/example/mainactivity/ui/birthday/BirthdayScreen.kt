@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.birthday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.data.BirthdayModel
import com.example.mainactivity.ui.components.BirthdayPickerField
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FamilyTextField
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.components.RefreshOnResume
import com.example.mainactivity.ui.components.SwipeToRevealDelete
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun BirthdayScreen(
    onBack: () -> Unit,
    viewModel: BirthdayViewModel = viewModel(),
) {
    val birthdays by viewModel.birthdays.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    var showAdd by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    val today = remember { LocalDate.now() }
    val sorted =
        remember(birthdays) {
            birthdays.sortedWith { a, b ->
                val nextA = nextBirthdayDate(a.date, today) ?: LocalDate.MAX
                val nextB = nextBirthdayDate(b.date, today) ?: LocalDate.MAX
                nextA.compareTo(nextB)
            }
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Birthdays", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add birthday") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        } else if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.Cake, "No birthdays", "Add family birthdays so you never miss a celebration.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.id }) { b ->
                    SwipeToRevealDelete(onDelete = { viewModel.delete(b) }, shape = RoundedCornerShape(20.dp)) {
                        BirthdayCard(b, today)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddBirthdayDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, date ->
                viewModel.add(name, date)
                showAdd = false
            },
        )
    }
}

@Composable
private fun BirthdayCard(
    b: BirthdayModel,
    today: LocalDate,
) {
    val nextDate = nextBirthdayDate(b.date, today)
    val age = turnsAge(b.date, today)
    val displayDate =
        if (nextDate != null) {
            nextDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        } else {
            b.date
        }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Cake, null, tint = MaterialTheme.colorScheme.tertiary) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(displayDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (age != null) {
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "Turns $age",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBirthdayDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Add birthday") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FamilyTextField(name, { name = it }, "Name")
                BirthdayPickerField(value = date, onChange = { date = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), date) },
                enabled = name.isNotBlank() && date.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun nextBirthdayDate(
    isoDate: String,
    today: LocalDate = LocalDate.now(),
): LocalDate? =
    runCatching {
        val bd = LocalDate.parse(isoDate)
        val thisYear = bd.withYear(today.year)
        if (thisYear < today) bd.withYear(today.year + 1) else thisYear
    }.getOrNull()

private fun turnsAge(
    isoDate: String,
    today: LocalDate = LocalDate.now(),
): Int? =
    runCatching {
        val bd = LocalDate.parse(isoDate)
        val next = nextBirthdayDate(isoDate, today) ?: return@runCatching null
        next.year - bd.year
    }.getOrNull()
