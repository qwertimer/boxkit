package com.qwertimer.forge.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qwertimer.forge.domain.model.BlockType
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.domain.model.WorkoutBlock
import com.qwertimer.forge.domain.model.WorkoutPlan
import com.qwertimer.forge.ui.common.Pill
import com.qwertimer.forge.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var skipTarget by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Training") }) },
        floatingActionButton = {
            // Always available. A rest day is a default, not a rule, and some days you want two.
            ExtendedFloatingActionButton(
                onClick = viewModel::addSession,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (state.sessions.isEmpty()) "Train anyway" else "Add session") },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.sessions.isEmpty()) {
                    item { RestDay(isRestDay = state.isRestDay) }
                }

                state.sessions.forEach { plan ->
                    item(key = "head-${plan.id}") {
                        SessionHeader(
                            plan = plan,
                            streak = stats?.currentStreak ?: 0,
                            onReroll = { viewModel.reroll(plan.id) },
                            onDelete = { viewModel.deleteSession(plan.id) },
                        )
                    }

                    BlockType.entries.forEach { type ->
                        val blocks = plan.blocks.filter { it.blockType == type }
                        if (blocks.isEmpty()) return@forEach
                        item(key = "sec-${plan.id}-$type") { SectionHeader(type.label) }
                        items(blocks, key = { it.id }) { block ->
                            BlockRow(block, enabled = plan.status == PlanStatus.PENDING) { checked ->
                                viewModel.toggleBlock(block.id, checked)
                            }
                        }
                    }

                    if (plan.status == PlanStatus.PENDING) {
                        item(key = "actions-${plan.id}") {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { viewModel.markComplete(plan.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Mark session complete") }

                                // Only the programmed session can be "skipped" — an extra session
                                // you decide against is simply deleted, with nothing to explain.
                                if (plan.isScheduled) {
                                    OutlinedButton(
                                        onClick = { skipTarget = plan.id },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Skip today") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    skipTarget?.let { planId ->
        SkipDialog(
            onDismiss = { skipTarget = null },
            onConfirm = { reason ->
                viewModel.skip(planId, reason)
                skipTarget = null
            },
        )
    }
}

@Composable
private fun SessionHeader(
    plan: WorkoutPlan,
    streak: Int,
    onReroll: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        plan.focus.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "~${plan.estimatedMinutes} min · ${plan.blocks.size} movements",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    plan.status == PlanStatus.COMPLETED -> Pill("Done")
                    plan.status == PlanStatus.SKIPPED -> Pill("Skipped")
                    !plan.isScheduled -> Pill("Extra")
                    streak > 0 -> Pill("$streak-day streak")
                }
            }

            LinearProgressIndicator(progress = { plan.progress }, modifier = Modifier.fillMaxWidth())

            plan.skipReason?.let {
                Text(
                    "Skipped: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (plan.status == PlanStatus.PENDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReroll, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Casino, contentDescription = null)
                        Text("  New routine")
                    }
                    if (!plan.isScheduled) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove session")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(block: WorkoutBlock, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (block.completed) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = block.completed, onCheckedChange = onToggle, enabled = enabled)
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                Text(block.exercise.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = block.prescription +
                        if (block.restSeconds > 0) " · ${block.restSeconds}s rest" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = block.exercise.cue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RestDay(isRestDay: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isRestDay) "Rest day" else "Nothing scheduled",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isRestDay) {
                    "Nothing will nag you today. Hit the button if you want to train anyway — " +
                        "it counts as a bonus and can't break your streak."
                } else {
                    "Add a session whenever you like."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A skip is always allowed and always costs you a written reason. */
@Composable
fun SkipDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    val valid = reason.trim().length >= MIN_REASON_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Why are you skipping?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "It gets recorded against the day. Future you gets to read it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    supportingText = {
                        if (!valid) Text("At least $MIN_REASON_LENGTH characters.")
                    },
                    isError = reason.isNotEmpty() && !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = valid) { Text("Record skip") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MIN_REASON_LENGTH = 5
