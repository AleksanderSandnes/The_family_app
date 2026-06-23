package com.example.mainactivity.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.data.CalendarEventModel
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val SHORT_DATE_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val SECTION_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val WEEKDAY_LABELS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

private data class CalendarIconOption(val key: String, val vector: ImageVector)

private val CALENDAR_ICON_OPTIONS = listOf(
    CalendarIconOption("schedule", Icons.Filled.Schedule),
    CalendarIconOption("cake", Icons.Filled.Cake),
    CalendarIconOption("people", Icons.Filled.People),
    CalendarIconOption("work", Icons.Filled.Work),
    CalendarIconOption("school", Icons.Filled.School),
    CalendarIconOption("restaurant", Icons.Filled.Restaurant),
    CalendarIconOption("flight", Icons.Filled.Flight),
    CalendarIconOption("local_hospital", Icons.Filled.LocalHospital),
    CalendarIconOption("celebration", Icons.Filled.Celebration),
    CalendarIconOption("shopping_cart", Icons.Filled.ShoppingCart),
    CalendarIconOption("music_note", Icons.Filled.MusicNote),
    CalendarIconOption("fitness_center", Icons.Filled.FitnessCenter),
    CalendarIconOption("wb_sunny", Icons.Filled.WbSunny),
    CalendarIconOption("favorite", Icons.Filled.Favorite),
    CalendarIconOption("star", Icons.Filled.Star),
    CalendarIconOption("emoji_events", Icons.Filled.EmojiEvents),
)

private fun iconVector(key: String): ImageVector =
    CALENDAR_ICON_OPTIONS.find { it.key == key }?.vector ?: Icons.Filled.Schedule

private fun parseHh(t: String) = t.substringBefore(":").toIntOrNull()?.coerceIn(0, 23) ?: 9
private fun parseMm(t: String) = t.substringAfter(":").toIntOrNull()?.coerceIn(0, 59) ?: 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val displayedMonth by viewModel.displayedMonth.collectAsStateWithLifecycle()
    val dayEvents by viewModel.eventsForSelectedDate.collectAsStateWithLifecycle()
    val allEvents by viewModel.events.collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<CalendarEventModel?>(null) }

    val datesWithEvents = remember(allEvents) {
        buildSet<LocalDate> {
            allEvents.forEach { e ->
                runCatching { add(LocalDate.parse(e.dateFrom)) }
                runCatching { add(LocalDate.parse(e.dateTo)) }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Calendar") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, "New event", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MonthCalendarSection(
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
                datesWithEvents = datesWithEvents,
                onPrevMonth = viewModel::prevMonth,
                onNextMonth = viewModel::nextMonth,
                onDaySelected = viewModel::selectDate
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text(
                selectedDate.format(SECTION_DATE_FMT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            if (dayEvents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Filled.CalendarMonth, "No events", "Tap + to add an event.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayEvents, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            onDelete = { viewModel.delete(event) },
                            onEdit = { eventToEdit = event }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        EventDialog(
            existingEvent = null,
            initialDate = selectedDate,
            onDismiss = { showAdd = false },
            onSave = { activity, allDay, dateFrom, dateTo, timeFrom, timeTo, icon ->
                viewModel.addEvent(activity, allDay, dateFrom, dateTo, timeFrom, timeTo, icon)
                showAdd = false
            }
        )
    }

    eventToEdit?.let { event ->
        EventDialog(
            existingEvent = event,
            initialDate = selectedDate,
            onDismiss = { eventToEdit = null },
            onSave = { activity, allDay, dateFrom, dateTo, timeFrom, timeTo, icon ->
                viewModel.updateEvent(
                    event.copy(
                        activity = activity,
                        allDay = allDay,
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        timeFrom = timeFrom,
                        timeTo = timeTo,
                        icon = icon
                    )
                )
                eventToEdit = null
            }
        )
    }
}

@Composable
private fun MonthCalendarSection(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    datesWithEvents: Set<LocalDate>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (LocalDate) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        MonthHeader(month = displayedMonth, onPrev = onPrevMonth, onNext = onNextMonth)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            WEEKDAY_LABELS.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        val today = LocalDate.now()
        AnimatedContent(
            targetState = displayedMonth,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { dir * it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -dir * it } + fadeOut())
            },
            label = "MonthGrid"
        ) { month ->
            Column(Modifier.fillMaxWidth()) {
                monthCells(month).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                hasEvents = date != null && date in datesWithEvents,
                                modifier = Modifier.weight(1f),
                                onClick = { if (date != null) onDaySelected(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            month.format(MONTH_YEAR_FMT),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (date != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (date == null) return@Box
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            val textColor = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .then(
                        if (isToday && !isSelected)
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${date.dayOfMonth}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = textColor
                )
            }
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasEvents && !isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
            )
        }
    }
}

private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // Monday-first; Monday.value=1 → offset 0
    val result = mutableListOf<LocalDate?>()
    repeat(offset) { result.add(null) }
    for (d in 1..month.lengthOfMonth()) result.add(month.atDay(d))
    while (result.size % 7 != 0) result.add(null)
    return result
}

@Composable
private fun EventCard(
    event: CalendarEventModel,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val subtitle = eventSubtitle(event)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onEdit() }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconVector(event.icon),
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.activity,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun eventSubtitle(event: CalendarEventModel): String {
    fun fmtDate(s: String) = try { LocalDate.parse(s).format(SHORT_DATE) } catch (_: Exception) { s }
    val from = fmtDate(event.dateFrom)
    val to = fmtDate(event.dateTo)
    val dateRange = if (to.isBlank() || from == to) from else "$from – $to"
    return if (event.allDay) {
        listOf("All day", dateRange).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        val timeRange = listOf(event.timeFrom, event.timeTo)
            .filter { it.isNotBlank() }
            .joinToString(" – ")
        listOf(dateRange, timeRange).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

@Composable
private fun IconPickerGrid(selected: String, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        CALENDAR_ICON_OPTIONS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { opt ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected == opt.key) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onSelect(opt.key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            opt.vector,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = if (selected == opt.key) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDialog(
    existingEvent: CalendarEventModel?,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (activity: String, allDay: Boolean, dateFrom: String, dateTo: String, timeFrom: String, timeTo: String, icon: String) -> Unit
) {
    val isEdit = existingEvent != null
    var activity by remember { mutableStateOf(existingEvent?.activity ?: "") }
    var allDay by remember { mutableStateOf(existingEvent?.allDay ?: false) }
    var selectedIcon by remember { mutableStateOf(existingEvent?.icon ?: "schedule") }
    var showIconPicker by remember { mutableStateOf(false) }
    var dateFrom by remember {
        mutableStateOf(
            existingEvent?.let { runCatching { LocalDate.parse(it.dateFrom) }.getOrNull() } ?: initialDate
        )
    }
    var dateTo by remember {
        mutableStateOf(
            existingEvent?.let { runCatching { LocalDate.parse(it.dateTo) }.getOrNull() } ?: initialDate
        )
    }
    val timeFromState = rememberTimePickerState(
        initialHour = existingEvent?.timeFrom?.let { parseHh(it) } ?: 9,
        initialMinute = existingEvent?.timeFrom?.let { parseMm(it) } ?: 0
    )
    val timeToState = rememberTimePickerState(
        initialHour = existingEvent?.timeTo?.let { parseHh(it) } ?: 10,
        initialMinute = existingEvent?.timeTo?.let { parseMm(it) } ?: 0
    )
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }
    var showTimeFromPicker by remember { mutableStateOf(false) }
    var showTimeToPicker by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (isEdit) "Edit event" else "New event",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { showIconPicker = !showIconPicker },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            iconVector(selectedIcon),
                            "Change icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = activity,
                        onValueChange = { activity = it },
                        placeholder = { Text("Event name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                AnimatedVisibility(visible = showIconPicker) {
                    IconPickerGrid(
                        selected = selectedIcon,
                        onSelect = { selectedIcon = it; showIconPicker = false }
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("All day", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DateTimeRow(
                    label = "Starts",
                    date = dateFrom,
                    time = if (!allDay) LocalTime.of(timeFromState.hour, timeFromState.minute) else null,
                    onDateClick = { showDateFromPicker = true },
                    onTimeClick = { showTimeFromPicker = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                DateTimeRow(
                    label = "Ends",
                    date = dateTo,
                    time = if (!allDay) LocalTime.of(timeToState.hour, timeToState.minute) else null,
                    onDateClick = { showDateToPicker = true },
                    onTimeClick = { showTimeToPicker = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (showError) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Event name is required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (activity.isNotBlank()) {
                    val tf = "%02d:%02d".format(timeFromState.hour, timeFromState.minute)
                    val tt = "%02d:%02d".format(timeToState.hour, timeToState.minute)
                    onSave(
                        activity.trim(), allDay,
                        dateFrom.toString(), dateTo.toString(),
                        if (allDay) "" else tf,
                        if (allDay) "" else tt,
                        selectedIcon
                    )
                } else {
                    showError = true
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDateFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDateFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showDateToPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTo.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDateToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showTimeFromPicker) {
        TimePickerDialog(
            title = "Start time",
            state = timeFromState,
            onDismiss = { showTimeFromPicker = false },
            onConfirm = { showTimeFromPicker = false }
        )
    }

    if (showTimeToPicker) {
        TimePickerDialog(
            title = "End time",
            state = timeToState,
            onDismiss = { showTimeToPicker = false },
            onConfirm = { showTimeToPicker = false }
        )
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable(onClick = onDateClick)
        ) {
            Text(
                date.format(SHORT_DATE_DAY),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        if (time != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onTimeClick)
            ) {
                Text(
                    time.format(TIME_FMT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                TimePicker(state = state)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}
