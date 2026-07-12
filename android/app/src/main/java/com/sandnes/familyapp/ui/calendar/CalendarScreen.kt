@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.CalendarEventModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.ui.components.AppTopBar
import com.sandnes.familyapp.ui.components.ColorPickerRow
import com.sandnes.familyapp.ui.components.ConfirmationDialog
import com.sandnes.familyapp.ui.components.CreationSheet
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.IconGrid
import com.sandnes.familyapp.ui.components.InitialAvatar
import com.sandnes.familyapp.ui.components.LoadingState
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SheetField
import com.sandnes.familyapp.ui.components.SwipeToRevealDelete
import com.sandnes.familyapp.ui.components.appSwitchColors
import com.sandnes.familyapp.ui.theme.AppColorPalette
import com.sandnes.familyapp.ui.theme.FeatureAccent
import com.sandnes.familyapp.ui.theme.FeatureBadge
import com.sandnes.familyapp.ui.theme.IconKeyMap
import com.sandnes.familyapp.ui.theme.IconOptions
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.calendarIconColorIndex
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.ui.theme.glassChrome
import com.sandnes.familyapp.ui.theme.hexColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val SECTION_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

// Monday-first, locale-aware two-letter weekday labels (e.g. EN "Mo Tu…", NB "Ma Ti…").
private val WEEKDAY_LABELS =
    java.time.DayOfWeek.entries.map { day ->
        day
            .getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, Locale.getDefault())
            .take(2)
            .replaceFirstChar { it.uppercase() }
    }

private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59
private const val DEFAULT_EVENT_HOUR = 9
private const val DAYS_PER_WEEK = 7
private const val MAX_MULTIDAY_SPAN = 60L

/**
 * Adaptive feature-derived dot colours used when an event has no custom colour. Mirrors iOS
 * `calendarDotFallback`; indexed via [calendarIconColorIndex]. These are fixed [FeatureAccent.dot]
 * values (identical in light/dark), so the map can be built outside composition.
 */
private val CALENDAR_DOT_FALLBACK: List<Color> =
    listOf(
        FeatureAccent.Calendar.dot,
        FeatureAccent.Shopping.dot,
        FeatureAccent.Birthdays.dot,
        FeatureAccent.Meals.dot,
        FeatureAccent.Wishlists.dot,
        FeatureAccent.Map.dot,
    )

/** An event's display colour — the user-picked colour, else an icon-derived accent. */
private fun calendarEventColor(event: CalendarEventModel): Color =
    hexColor(event.color)
        ?: CALENDAR_DOT_FALLBACK[calendarIconColorIndex(event.icon) % CALENDAR_DOT_FALLBACK.size]

/** Maps each date to the display colours of events covering it (multi-day capped at 60 days). */
private fun dateEventColors(events: List<CalendarEventModel>): Map<LocalDate, List<Color>> =
    buildMap<LocalDate, MutableList<Color>> {
        events.forEach { e ->
            val from = runCatching { LocalDate.parse(e.dateFrom) }.getOrNull() ?: return@forEach
            val to = runCatching { LocalDate.parse(e.dateTo) }.getOrElse { from }
            val color = calendarEventColor(e)
            var d = from
            while (!d.isAfter(to)) {
                getOrPut(d) { mutableListOf() }.add(color)
                d = d.plusDays(1)
                if (d.isAfter(from.plusDays(MAX_MULTIDAY_SPAN))) break
            }
        }
    }

private fun parseHh(t: String) = t.substringBefore(":").toIntOrNull()?.coerceIn(0, MAX_HOUR) ?: DEFAULT_EVENT_HOUR

private fun parseMm(t: String) = t.substringAfter(":").toIntOrNull()?.coerceIn(0, MAX_MINUTE) ?: 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val displayedMonth by viewModel.displayedMonth.collectAsStateWithLifecycle()
    val dayEvents by viewModel.eventsForSelectedDate.collectAsStateWithLifecycle()
    val allEvents by viewModel.events.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle(emptyList())
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle(null)
    var showAdd by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<CalendarEventModel?>(null) }
    var pendingDelete by remember { mutableStateOf<CalendarEventModel?>(null) }
    var view by remember { mutableStateOf(CalendarView.Month) }

    RefreshOnResume { viewModel.refresh() }

    val otherMembers = remember(familyMembers, currentUserId) { familyMembers.filter { it.id != currentUserId } }

    // Map each date to the display colours of events covering it (max 3 dots shown per day).
    val dateColors: Map<LocalDate, List<Color>> = remember(allEvents) { dateEventColors(allEvents) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // iOS header: "Today" pill + circular "+" both in the top bar (no FAB).
            AppTopBar(
                title = stringResource(R.string.calendar),
                actions = {
                    TextButton(onClick = { viewModel.selectDate(LocalDate.now()) }) {
                        Text(stringResource(R.string.today))
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_new_calendar_event),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CalendarViewToggle(view = view, onSelect = { view = it })
            when (view) {
                CalendarView.Month -> {
                    MonthCalendarSection(
                        displayedMonth = displayedMonth,
                        selectedDate = selectedDate,
                        dateColors = dateColors,
                        onPrevMonth = viewModel::prevMonth,
                        onNextMonth = viewModel::nextMonth,
                        onDaySelected = viewModel::selectDate,
                    )
                    SelectedDateHeader(selectedDate)
                    DayEventsList(isLoading, dayEvents, familyMembers, { eventToEdit = it }, { pendingDelete = it })
                }
                CalendarView.Week -> {
                    WeekStrip(selectedDate, dateColors, viewModel::selectDate)
                    SelectedDateHeader(selectedDate)
                    DayEventsList(isLoading, dayEvents, familyMembers, { eventToEdit = it }, { pendingDelete = it })
                }
                CalendarView.Agenda ->
                    AgendaList(
                        modifier = Modifier.weight(1f),
                        events = allEvents,
                        members = familyMembers,
                        onEdit = { eventToEdit = it },
                        onDelete = { pendingDelete = it },
                    )
            }
        }
    }

    pendingDelete?.let { event ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_event_q),
            message = stringResource(R.string.delete_event_confirm, event.activity),
            onConfirm = {
                viewModel.delete(event)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showAdd) {
        EventDialog(
            existingEvent = null,
            initialDate = selectedDate,
            members = otherMembers,
            onDismiss = { showAdd = false },
            onSave = { draft ->
                viewModel.addEvent(draft)
                showAdd = false
            },
        )
    }

    eventToEdit?.let { event ->
        EventDialog(
            existingEvent = event,
            initialDate = selectedDate,
            members = otherMembers,
            onDismiss = { eventToEdit = null },
            onSave = { draft ->
                viewModel.updateEvent(
                    event.copy(
                        activity = draft.activity,
                        allDay = draft.allDay,
                        dateFrom = draft.dateFrom,
                        dateTo = draft.dateTo,
                        timeFrom = draft.timeFrom,
                        timeTo = draft.timeTo,
                        icon = draft.icon,
                        isPrivate = draft.isPrivate,
                        color = draft.color,
                        attendeeIds = draft.attendeeIds,
                    ),
                )
                eventToEdit = null
            },
        )
    }
}

private enum class CalendarView(
    @StringRes val labelRes: Int,
) {
    Month(R.string.month),
    Week(R.string.week),
    Agenda(R.string.agenda),
}

/** Glass segmented control for the Month / Week / Agenda modes. */
@Composable
private fun CalendarViewToggle(
    view: CalendarView,
    onSelect: (CalendarView) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = 6.dp)
            .glassChrome(Radius.segment)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarView.entries.forEach { v ->
            val selected = v == view
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.segmentThumb))
                    // iOS-style thumb: white/surface pill lifts off the glass track.
                    .then(
                        if (selected) {
                            Modifier
                                .shadow(2.dp, RoundedCornerShape(Radius.segmentThumb))
                                .background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier
                        },
                    ).clickable { onSelect(v) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(v.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectedDateHeader(date: LocalDate) {
    Text(
        date.format(SECTION_DATE_FMT).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screenEdge + 6.dp, vertical = 14.dp),
    )
}

/** The selected day's events (loading / empty / list) — shared by Month and Week views. */
@Composable
private fun ColumnScope.DayEventsList(
    isLoading: Boolean,
    dayEvents: List<CalendarEventModel>,
    members: List<UserModel>,
    onEdit: (CalendarEventModel) -> Unit,
    onDelete: (CalendarEventModel) -> Unit,
) {
    when {
        isLoading ->
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        dayEvents.isEmpty() ->
            // Scrollable so the empty state is never clipped under the nav bar on short screens.
            Box(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    Icons.Filled.CalendarMonth,
                    stringResource(R.string.no_events),
                    stringResource(R.string.tap_to_add_an_event),
                )
            }
        else ->
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.screenEdge, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(dayEvents, key = { it.id }) { event ->
                    SwipeToRevealDelete(onDelete = { onDelete(event) }, shape = RoundedCornerShape(Radius.row)) {
                        EventCard(event = event, members = members, onEdit = { onEdit(event) })
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
    }
}

/** Single-week strip for the Week view — reuses the month grid's DayCell. */
@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    dateColors: Map<LocalDate, List<Color>>,
    onDaySelected: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = 6.dp)
            .glassCard(Radius.bigCard)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        WeekdayHeaderRow()
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val date = monday.plusDays(i.toLong())
                DayCell(
                    date = date,
                    isSelected = date == selectedDate,
                    isToday = date == today,
                    dotColors = dateColors[date].orEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = { onDaySelected(date) },
                )
            }
        }
    }
}

/** Chronological list of all upcoming events, grouped by date. */
@Composable
private fun AgendaList(
    modifier: Modifier = Modifier,
    events: List<CalendarEventModel>,
    members: List<UserModel>,
    onEdit: (CalendarEventModel) -> Unit,
    onDelete: (CalendarEventModel) -> Unit,
) {
    val today = LocalDate.now()
    val grouped =
        remember(events) {
            events
                .filter { e ->
                    val end = runCatching { LocalDate.parse(e.dateTo.ifBlank { e.dateFrom }) }.getOrNull()
                    end != null && !end.isBefore(today)
                }.sortedBy { runCatching { LocalDate.parse(it.dateFrom) }.getOrNull() ?: LocalDate.MAX }
                .groupBy { runCatching { LocalDate.parse(it.dateFrom) }.getOrNull() }
        }
    if (grouped.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(
                Icons.Filled.CalendarMonth,
                stringResource(R.string.nothing_coming_up),
                stringResource(R.string.tap_to_add_an_event),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.screenEdge, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { (date, dayEvents) ->
            item(key = "header-$date") {
                Text(
                    date?.format(SECTION_DATE_FMT) ?: stringResource(R.string.upcoming),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(dayEvents, key = { it.id }) { event ->
                SwipeToRevealDelete(onDelete = { onDelete(event) }, shape = RoundedCornerShape(Radius.row)) {
                    EventCard(event = event, members = members, onEdit = { onEdit(event) })
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MonthCalendarSection(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    dateColors: Map<LocalDate, List<Color>>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = 6.dp)
            .glassCard(Radius.bigCard)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        MonthHeader(month = displayedMonth, onPrev = onPrevMonth, onNext = onNextMonth)
        WeekdayHeaderRow()
        val today = LocalDate.now()
        AnimatedContent(
            targetState = displayedMonth,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { dir * it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -dir * it } + fadeOut())
            },
            label = "MonthGrid",
        ) { month ->
            Column(Modifier.fillMaxWidth()) {
                monthCells(month).chunked(DAYS_PER_WEEK).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                dotColors = if (date != null) dateColors[date].orEmpty() else emptyList(),
                                modifier = Modifier.weight(1f),
                                onClick = { if (date != null) onDaySelected(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        WEEKDAY_LABELS.forEach { label ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month), tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            month.format(MONTH_YEAR_FMT),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isSelected: Boolean,
    isToday: Boolean,
    dotColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dayName =
        date
            ?.dayOfWeek
            ?.name
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() } ?: ""
    val monthName =
        date
            ?.month
            ?.name
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() } ?: ""
    val eventCount = dotColors.size
    val a11yDesc =
        if (date != null) {
            "$dayName, $monthName ${date.dayOfMonth}. $eventCount ${if (eventCount == 1) "event" else "events"}"
        } else {
            null
        }

    Box(
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .aspectRatio(1f)
                .then(
                    if (date != null) {
                        Modifier.clickable(onClickLabel = a11yDesc, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (date == null) return@Box
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val bgColor =
                when {
                    isToday -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            val textColor =
                when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .then(
                            if (isSelected && !isToday) {
                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${date.dayOfMonth}",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                    color = textColor,
                )
            }
            Spacer(Modifier.height(2.dp))
            // Up to 3 dots coloured by each event's display colour.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (dotColors.isEmpty()) {
                    Spacer(Modifier.size(6.dp))
                } else {
                    dotColors.take(3).forEach { dot ->
                        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                    }
                }
            }
        }
    }
}

private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // Monday-first; Monday.value=1 → offset 0
    val result = mutableListOf<LocalDate?>()
    repeat(offset) { result.add(null) }
    for (d in 1..month.lengthOfMonth()) result.add(month.atDay(d))
    while (result.size % DAYS_PER_WEEK != 0) result.add(null)
    return result
}

@Composable
private fun EventCard(
    event: CalendarEventModel,
    members: List<UserModel>,
    onEdit: () -> Unit,
) {
    val timeLabel = eventTimeLabel(event)
    val accent = calendarEventColor(event)
    // Creator first, then attendees — mirrors iOS EventCard people row.
    val people =
        remember(event, members) {
            (listOf(event.userId) + event.attendeeIds).mapNotNull { id -> members.find { it.id == id } }
        }
    Box(
        Modifier
            .fillMaxWidth()
            .glassCard(Radius.row)
            .clickable { onEdit() }
            .padding(horizontal = Spacing.lg, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeatureBadge(
                icon = IconKeyMap.calendar(event.icon),
                feature = FeatureAccent.Calendar,
                size = 42.dp,
                colorOverride = accent,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    event.activity,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (timeLabel.isNotBlank()) {
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (people.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    EventPeopleRow(people)
                }
            }
        }
    }
}

/** Names when there are few; overlapping avatars when they'd overflow the row. */
@Composable
private fun EventPeopleRow(people: List<UserModel>) {
    if (people.size <= 2) {
        Text(
            people.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            people.take(6).forEach { person ->
                InitialAvatar(
                    name = person.name,
                    color = Color(person.avatarColor.takeIf { it != 0 } ?: 0xFF6366F1.toInt()),
                    size = 22,
                    avatarUri = person.avatarUrl,
                )
            }
        }
    }
}

/** Returns a concise time/date label for the event card. */
@Composable
private fun eventTimeLabel(event: CalendarEventModel): String {
    if (event.allDay) return stringResource(R.string.all_day)
    return listOf(event.timeFrom, event.timeTo)
        .filter { it.isNotBlank() }
        .joinToString(" – ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDialog(
    existingEvent: CalendarEventModel?,
    initialDate: LocalDate,
    members: List<UserModel>,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    val isEdit = existingEvent != null
    var activity by remember { mutableStateOf(existingEvent?.activity ?: "") }
    var allDay by remember { mutableStateOf(existingEvent?.allDay ?: false) }
    var isPrivate by remember { mutableStateOf(existingEvent?.isPrivate ?: false) }
    var selectedIcon by remember { mutableStateOf(existingEvent?.icon ?: "schedule") }
    // New events default to the first palette colour; edits keep their stored colour.
    var color by remember { mutableStateOf(existingEvent?.color ?: AppColorPalette.first()) }
    val attendeeIds: SnapshotStateList<String> =
        remember { mutableStateListOf<String>().apply { addAll(existingEvent?.attendeeIds ?: emptyList()) } }
    var dateFrom by remember {
        mutableStateOf(existingEvent?.let { runCatching { LocalDate.parse(it.dateFrom) }.getOrNull() } ?: initialDate)
    }
    var dateTo by remember {
        mutableStateOf(existingEvent?.let { runCatching { LocalDate.parse(it.dateTo) }.getOrNull() } ?: initialDate)
    }
    val timeFromState =
        rememberTimePickerState(
            initialHour = existingEvent?.timeFrom?.let { parseHh(it) } ?: 9,
            initialMinute = existingEvent?.timeFrom?.let { parseMm(it) } ?: 0,
        )
    val timeToState =
        rememberTimePickerState(
            initialHour = existingEvent?.timeTo?.let { parseHh(it) } ?: 10,
            initialMinute = existingEvent?.timeTo?.let { parseMm(it) } ?: 0,
        )
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }
    var showTimeFromPicker by remember { mutableStateOf(false) }
    var showTimeToPicker by remember { mutableStateOf(false) }
    val overrideColor = hexColor(color)

    // iOS-parity event sheet (New event / Edit event).
    CreationSheet(
        title = stringResource(if (isEdit) R.string.edit_event else R.string.new_event),
        confirmTitle = stringResource(R.string.save),
        confirmEnabled = activity.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            val tf = "%02d:%02d".format(timeFromState.hour, timeFromState.minute)
            val tt = "%02d:%02d".format(timeToState.hour, timeToState.minute)
            onSave(
                EventDraft(
                    activity = activity.trim(),
                    allDay = allDay,
                    dateFrom = dateFrom.toString(),
                    dateTo = dateTo.toString(),
                    timeFrom = if (allDay) "" else tf,
                    timeTo = if (allDay) "" else tt,
                    icon = selectedIcon,
                    isPrivate = isPrivate,
                    color = color,
                    attendeeIds = members.map { it.id }.filter { attendeeIds.contains(it) },
                ),
            )
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetField(
                icon = IconKeyMap.calendar(selectedIcon),
                placeholder = stringResource(R.string.event_name),
                value = activity,
                onValueChange = { activity = it },
            )

            DialogSectionLabel(stringResource(R.string.icon))
            IconGrid(
                options = IconOptions.calendar,
                selected = selectedIcon,
                onSelect = { selectedIcon = it },
                feature = FeatureAccent.Calendar,
                colorOverride = overrideColor,
            )

            DialogSectionLabel(stringResource(R.string.color))
            ColorPickerRow(selected = color, onSelect = { color = it })

            Spacer(Modifier.height(12.dp))
            ToggleRow(stringResource(R.string.private_label), isPrivate) { isPrivate = it }
            ToggleRow(stringResource(R.string.all_day), allDay) { allDay = it }
            DateTimeRow(
                label = stringResource(R.string.starts),
                date = dateFrom,
                time = if (!allDay) LocalTime.of(timeFromState.hour, timeFromState.minute) else null,
                onDateClick = { showDateFromPicker = true },
                onTimeClick = { showTimeFromPicker = true },
            )
            DateTimeRow(
                label = stringResource(R.string.ends),
                date = dateTo,
                time = if (!allDay) LocalTime.of(timeToState.hour, timeToState.minute) else null,
                onDateClick = { showDateToPicker = true },
                onTimeClick = { showTimeToPicker = true },
            )

            if (members.isNotEmpty()) {
                DialogSectionLabel(stringResource(R.string.going_with))
                members.forEach { member ->
                    AttendeeRow(
                        member = member,
                        selected = attendeeIds.contains(member.id),
                        onToggle = {
                            if (attendeeIds.contains(member.id)) {
                                attendeeIds.remove(member.id)
                            } else {
                                attendeeIds.add(member.id)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showDateFromPicker) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        dateFrom = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (dateTo.isBefore(dateFrom)) dateTo = dateFrom
                    }
                    showDateFromPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDateFromPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showDateToPicker) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = dateTo.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        dateTo = if (picked.isBefore(dateFrom)) dateFrom else picked
                    }
                    showDateToPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDateToPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showTimeFromPicker) {
        TimePickerDialog(
            title = stringResource(R.string.start_time),
            state = timeFromState,
            onDismiss = { showTimeFromPicker = false },
            onConfirm = { showTimeFromPicker = false },
        )
    }

    if (showTimeToPicker) {
        TimePickerDialog(
            title = stringResource(R.string.end_time),
            state = timeToState,
            onDismiss = { showTimeToPicker = false },
            onConfirm = { showTimeToPicker = false },
        )
    }
}

@Composable
private fun DialogSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange, colors = appSwitchColors())
    }
}

@Composable
private fun AttendeeRow(
    member: UserModel,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(
            name = member.name,
            color = Color(member.avatarColor.takeIf { it != 0 } ?: 0xFF6366F1.toInt()),
            size = 34,
            avatarUri = member.avatarUrl,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            member.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            if (selected) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = if (selected) "Selected" else "Add",
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable(onClick = onDateClick),
        ) {
            Text(
                date.format(SHORT_DATE_DAY),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (time != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onTimeClick),
            ) {
                Text(
                    time.format(TIME_FMT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    state: androidx.compose.material3.TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                TimePicker(state = state)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}
