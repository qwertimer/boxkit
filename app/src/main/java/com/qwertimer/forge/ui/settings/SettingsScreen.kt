package com.qwertimer.forge.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qwertimer.forge.domain.model.FitnessLevel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("Daily targets") {
                TargetFields(
                    kcal = settings.kcalTarget,
                    protein = settings.proteinTargetG,
                    carbs = settings.carbsTargetG,
                    fat = settings.fatTargetG,
                    onChange = viewModel::setTargets,
                )
            }

            SettingsCard("Training days") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in settings.trainingDays,
                            onClick = { viewModel.toggleTrainingDay(day) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                Text(
                    "Rest days are never nagged and never count against a streak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard("Session") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FitnessLevel.entries.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = settings.fitnessLevel == level,
                            onClick = { viewModel.setFitnessLevel(level) },
                            shape = SegmentedButtonDefaults.itemShape(index, FitnessLevel.entries.size),
                        ) { Text(level.label) }
                    }
                }
                SliderRow(
                    label = "Session length",
                    value = settings.sessionMinutes.toFloat(),
                    range = 10f..90f,
                    steps = 15,
                    display = "${settings.sessionMinutes} min",
                    onChange = { viewModel.setSessionMinutes(it.roundToInt()) },
                )
                SwitchRow(
                    label = "Allow jumping movements",
                    detail = "Turn off to drop plyometrics and other high-impact work.",
                    checked = settings.allowHighImpact,
                    onChange = viewModel::setAllowHighImpact,
                )
            }

            SettingsCard("Enforcement") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Reminder time", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            settings.reminderTime.format(TIME_FORMAT),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) { Text("Change") }
                }

                SliderRow(
                    label = "Nag interval",
                    value = settings.nagIntervalMinutes.toFloat(),
                    range = 5f..60f,
                    steps = 10,
                    display = "${settings.nagIntervalMinutes} min",
                    onChange = { viewModel.setNagInterval(it.roundToInt()) },
                )
                SliderRow(
                    label = "Nags before enforcement",
                    value = settings.maxNags.toFloat(),
                    range = 0f..8f,
                    steps = 7,
                    display = "${settings.maxNags}",
                    onChange = { viewModel.setMaxNags(it.roundToInt()) },
                )
                SwitchRow(
                    label = "Full-screen enforcement",
                    detail = "After the nags run out, take over the screen until the session is " +
                        "logged or explicitly skipped with a reason.",
                    checked = settings.fullScreenEnforcement,
                    onChange = viewModel::setFullScreenEnforcement,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Notification permissions") }

                OutlinedButton(
                    onClick = {
                        // Exact alarms are what keep the reminder honest to the minute.
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Alarms & battery settings") }
            }

            SettingsCard("AI nutrition lookup") {
                SwitchRow(
                    label = "Use Gemini when a food isn't in the database",
                    detail = "Grounded in Google Search. Estimates are marked as estimates.",
                    checked = settings.aiFallbackEnabled,
                    onChange = viewModel::setAiFallback,
                )
                var key by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { viewModel.setGeminiKey(key) },
                    enabled = key != settings.geminiApiKey,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save key") }

                var model by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { viewModel.setGeminiModel(model) },
                    enabled = model != settings.geminiModel && model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save model") }

                Text(
                    "The key is stored on this device only and is excluded from backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = settings.reminderTime,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.setReminderTime(it)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun TargetFields(
    kcal: Double,
    protein: Double,
    carbs: Double,
    fat: Double,
    onChange: (Double, Double, Double, Double) -> Unit,
) {
    var kcalText by remember(kcal) { mutableStateOf(kcal.roundToInt().toString()) }
    var proteinText by remember(protein) { mutableStateOf(protein.roundToInt().toString()) }
    var carbsText by remember(carbs) { mutableStateOf(carbs.roundToInt().toString()) }
    var fatText by remember(fat) { mutableStateOf(fat.roundToInt().toString()) }

    @Composable
    fun field(value: String, label: String, onValue: (String) -> Unit, modifier: Modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValue(it.filter(Char::isDigit)) },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = modifier,
        )
    }

    field(kcalText, "Calories", { kcalText = it }, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        field(proteinText, "Protein g", { proteinText = it }, Modifier.weight(1f))
        field(carbsText, "Carbs g", { carbsText = it }, Modifier.weight(1f))
        field(fatText, "Fat g", { fatText = it }, Modifier.weight(1f))
    }
    OutlinedButton(
        onClick = {
            onChange(
                kcalText.toDoubleOrNull() ?: kcal,
                proteinText.toDoubleOrNull() ?: protein,
                carbsText.toDoubleOrNull() ?: carbs,
                fatText.toDoubleOrNull() ?: fat,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save targets") }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(display, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) },
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
