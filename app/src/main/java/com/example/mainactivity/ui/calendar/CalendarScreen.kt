package com.example.mainactivity.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.data.CalendarEventEntity
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Calendar") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New event") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.CalendarMonth, "Nothing scheduled", "Add a family event to see it here.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    EventCard(event = event, onDelete = { viewModel.delete(event) })
                }
            }
        }
    }

    if (showAdd) {
        AddEventDialog(
            onDismiss = { showAdd = false },
            onConfirm = { activity, allDay, dateFrom, dateTo, timeFrom, timeTo ->
                viewModel.addEvent(activity, allDay, dateFrom, dateTo, timeFrom, timeTo)
                showAdd = false
            }
        )
    }
}

@Composable
private fun EventCard(event: CalendarEventEntity, onDelete: () -> Unit) {
    val subtitle = eventSubtitle(event)
    val icon = if (event.allDay) Icons.Filled.WbSunny else Icons.Filled.Schedule

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.activity,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun eventSubtitle(event: CalendarEventEntity): String {
    val dateRange = when {
        event.dateTo.isBlank() || event.dateFrom == event.dateTo -> event.dateFrom
        else -> "${event.dateFrom} – ${event.dateTo}"
    }
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
private fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (activity: String, allDay: Boolean, dateFrom: String, dateTo: String, timeFrom: String, timeTo: String) -> Unit
) {
    var activity by remember { mutableStateOf("") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var timeFrom by remember { mutableStateOf("") }
    var timeTo by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val isValid = activity.isNotBlank() && dateFrom.isNotBlank() && (allDay || timeFrom.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("New event", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    activity, { activity = it },
                    label = { Text("What's happening?") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    dateFrom, { dateFrom = it },
                    label = { Text("Start date (e.g. 24 Jun)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    dateTo, { dateTo = it },
                    label = { Text("End date (optional, leave blank if same day)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("All day", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                AnimatedVisibility(visible = !allDay) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            timeFrom, { timeFrom = it },
                            label = { Text("Start time (e.g. 09:00)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            timeTo, { timeTo = it },
                            label = { Text("End time (optional, e.g. 10:00)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (showError && !isValid) {
                    Text(
                        when {
                            activity.isBlank() -> "Event name is required."
                            dateFrom.isBlank() -> "Start date is required."
                            else -> "Start time is required for timed events."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isValid) {
                    onConfirm(activity.trim(), allDay, dateFrom.trim(), dateTo.trim(), timeFrom.trim(), timeTo.trim())
                } else {
                    showError = true
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
