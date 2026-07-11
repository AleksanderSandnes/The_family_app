@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.birthday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.BirthdayModel
import com.sandnes.familyapp.ui.components.AppFab
import com.sandnes.familyapp.ui.components.BirthdayPickerField
import com.sandnes.familyapp.ui.components.ColorPickerRow
import com.sandnes.familyapp.ui.components.CreationSheet
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.IconGrid
import com.sandnes.familyapp.ui.components.ListSkeleton
import com.sandnes.familyapp.ui.components.PillTag
import com.sandnes.familyapp.ui.components.PullRefresh
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SheetField
import com.sandnes.familyapp.ui.components.SwipeToRevealDelete
import com.sandnes.familyapp.ui.theme.AppColorPalette
import com.sandnes.familyapp.ui.theme.FeatureAccent
import com.sandnes.familyapp.ui.theme.IconKeyMap
import com.sandnes.familyapp.ui.theme.IconOptions
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.ui.theme.hexColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BIRTH_DATE_FMT =
    DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(Locale.getDefault())

@Composable
fun BirthdayScreen(
    onBack: () -> Unit,
    viewModel: BirthdayViewModel = hiltViewModel(),
) {
    val birthdays by viewModel.birthdays.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle(null)
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BirthdayModel?>(null) }

    RefreshOnResume { viewModel.refresh() }

    val today = remember { LocalDate.now() }
    // List stays sorted by the next upcoming occurrence, even though each card shows the birth date.
    val sorted =
        remember(birthdays) {
            birthdays.sortedWith { a, b ->
                val nextA = nextBirthdayDate(a.date, today) ?: LocalDate.MAX
                val nextB = nextBirthdayDate(b.date, today) ?: LocalDate.MAX
                nextA.compareTo(nextB)
            }
        }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopBar(stringResource(R.string.birthdays), onBack) },
        floatingActionButton = {
            AppFab(text = stringResource(R.string.add_birthday), icon = Icons.Filled.Add, onClick = { showAdd = true })
        },
    ) { padding ->
        PullRefresh(
            onRefresh = { viewModel.refresh().join() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (isLoading) {
                ListSkeleton(Modifier.fillMaxSize())
            } else if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Filled.Cake,
                        stringResource(R.string.no_birthdays),
                        stringResource(R.string.add_family_birthdays_so_you_never_miss_a_celebration),
                        actionLabel = stringResource(R.string.add_birthday),
                        onAction = { showAdd = true },
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.screenEdge),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sorted, key = { it.id }) { b ->
                        // Only the creator may edit or delete (auto-birthdays belong to the person; RLS enforces).
                        val canEdit = b.madeByUserId == currentUserId
                        if (canEdit) {
                            SwipeToRevealDelete(
                                onDelete = { viewModel.delete(b) },
                                modifier = Modifier.animateItem(),
                                shape = RoundedCornerShape(Radius.overviewCard),
                            ) {
                                BirthdayCard(b, today, onEdit = { editing = b })
                            }
                        } else {
                            BirthdayCard(b, today, onEdit = null, modifier = Modifier.animateItem())
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        BirthdayDialog(
            title = stringResource(R.string.add_birthday),
            confirmLabel = stringResource(R.string.add),
            onDismiss = { showAdd = false },
            onConfirm = { name, date, icon, color ->
                viewModel.add(name, date, icon, color)
                showAdd = false
            },
        )
    }

    editing?.let { birthday ->
        BirthdayDialog(
            title = stringResource(R.string.edit_birthday),
            confirmLabel = stringResource(R.string.save),
            initialName = birthday.name,
            initialDate = birthday.date,
            initialIcon = birthday.icon,
            initialColor = birthday.color,
            onDismiss = { editing = null },
            onConfirm = { name, date, icon, color ->
                viewModel.update(birthday.id, name, date, icon, color)
                editing = null
            },
        )
    }
}

@Composable
private fun BirthdayCard(
    b: BirthdayModel,
    today: LocalDate,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val nextDate = nextBirthdayDate(b.date, today)
    val age = turnsAge(b.date, today)
    val daysUntil = nextDate?.let { (it.toEpochDay() - today.toEpochDay()).toInt() }
    // Show the actual date of birth (year included), not the next occurrence.
    val displayDate =
        runCatching { LocalDate.parse(b.date).format(BIRTH_DATE_FMT) }.getOrDefault(b.date)
    val isToday = daysUntil == 0
    val accent = hexColor(b.color) ?: FeatureAccent.Birthdays.stroke()

    val daysLabel =
        when {
            daysUntil == 0 -> stringResource(R.string.today_exclaim)
            daysUntil != null -> stringResource(R.string.in_days, daysUntil)
            else -> ""
        }
    val cardDescription =
        "${b.name}'s birthday, $displayDate${if (daysLabel.isNotEmpty()) ", $daysLabel" else ""}"

    // Today's birthday gets a celebratory green ring; every card is a glass surface.
    val base =
        modifier
            .fillMaxWidth()
            .glassCard(Radius.overviewCard)
            .then(
                if (isToday) {
                    Modifier.border(1.5.dp, Color(0xFF22C55E).copy(alpha = 0.5f), RoundedCornerShape(Radius.overviewCard))
                } else {
                    Modifier
                },
            ).then(if (onEdit != null) Modifier.clickable { onEdit() } else Modifier)
            .padding(Spacing.cardPadding)
            .semantics { contentDescription = cardDescription }

    Row(base, verticalAlignment = Alignment.CenterVertically) {
        // Circular badge — accent at 16% fill with the accent glyph (mirrors the iOS row).
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IconKeyMap.birthday(b.icon),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                b.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            // FlowRow so "turning N" wraps as a whole chunk instead of mid-phrase when the
            // localized date + countdown pill leave little width.
            androidx.compose.foundation.layout.FlowRow {
                Text(
                    displayDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (age != null) {
                    Text(
                        " " + stringResource(R.string.turning_age, age),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                        maxLines = 1,
                    )
                }
            }
        }
        if (daysUntil != null) {
            Spacer(Modifier.size(8.dp))
            DaysPill(daysUntil)
        }
    }
}

/** Urgency-tinted countdown pill: Today (green), within a week (amber), otherwise muted. */
@Composable
private fun DaysPill(days: Int) {
    when (birthdayUrgency(days)) {
        BirthdayUrgency.TODAY ->
            PillTag(
                text = stringResource(R.string.today_exclaim) + " 🎉",
                container = Color(0xFF22C55E),
                content = Color.White,
            )
        BirthdayUrgency.SOON ->
            PillTag(
                text = stringResource(R.string.in_days, days),
                container = Color(0xFFF59E0B).copy(alpha = 0.18f),
                content = Color(0xFFB45309),
            )
        BirthdayUrgency.LATER ->
            PillTag(
                text = stringResource(R.string.in_days, days),
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
private fun BirthdayDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialDate: String = "",
    initialIcon: String = "cake",
    initialColor: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, date: String, icon: String, color: Int?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var date by remember { mutableStateOf(initialDate) }
    var icon by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableStateOf(initialColor ?: AppColorPalette.first()) }
    val overrideColor = hexColor(color)

    // iOS-parity birthday sheet (BirthdaySheet): name, date, icon grid, colour row.
    CreationSheet(
        title = title,
        confirmTitle = confirmLabel,
        confirmEnabled = name.isNotBlank() && date.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name.trim(), date, icon, color) },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetField(
                icon = Icons.Filled.Person,
                placeholder = stringResource(R.string.name),
                value = name,
                onValueChange = { name = it },
            )
            BirthdayPickerField(value = date, onChange = { date = it }, label = stringResource(R.string.birthday))

            SectionLabel(stringResource(R.string.icon))
            IconGrid(
                options = IconOptions.calendar,
                selected = icon,
                onSelect = { icon = it },
                feature = FeatureAccent.Birthdays,
                colorOverride = overrideColor,
            )

            SectionLabel(stringResource(R.string.color))
            ColorPickerRow(selected = color, onSelect = { color = it })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
