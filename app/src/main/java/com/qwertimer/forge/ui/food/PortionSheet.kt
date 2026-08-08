package com.qwertimer.forge.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.qwertimer.forge.domain.model.FoodItem
import com.qwertimer.forge.domain.model.MealType
import com.qwertimer.forge.ui.common.Pill
import kotlin.math.roundToInt

/** How the amount typed into the sheet is interpreted. */
private enum class PortionUnit(val label: String) {
    GRAMS("Grams"),
    SERVINGS("Servings"),
}

/**
 * "How much of it did you eat?" — the step between identifying a food and logging it.
 *
 * Servings are only offered when the product actually declares a serving weight; otherwise the
 * conversion would be a guess dressed up as a number.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortionSheet(
    food: FoodItem,
    suggestedGrams: Double?,
    initialMeal: MealType,
    onDismiss: () -> Unit,
    onConfirm: (grams: Double, label: String, meal: MealType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val servingSize = food.servingSizeG

    var unit by remember(food.id) {
        mutableStateOf(if (servingSize != null && suggestedGrams == null) PortionUnit.SERVINGS else PortionUnit.GRAMS)
    }
    var amount by remember(food.id) {
        mutableStateOf(
            when {
                suggestedGrams != null -> suggestedGrams.roundToInt().toString()
                servingSize != null -> "1"
                else -> "100"
            },
        )
    }
    var meal by remember(food.id) { mutableStateOf(initialMeal) }

    val parsed = amount.replace(',', '.').toDoubleOrNull()
    val grams = when {
        parsed == null || parsed <= 0.0 -> null
        unit == PortionUnit.GRAMS -> parsed
        else -> servingSize?.let { parsed * it }
    }
    val preview = grams?.let { food.per100g.forGrams(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(food.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                food.brand?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (food.source.isEstimate) Pill("AI estimate")
                    Text(
                        text = "${food.per100g.kcalRounded} kcal / 100 g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            food.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (servingSize != null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PortionUnit.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = unit == option,
                            onClick = { unit = option },
                            shape = SegmentedButtonDefaults.itemShape(index, PortionUnit.entries.size),
                        ) { Text(option.label) }
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text(if (unit == PortionUnit.GRAMS) "Grams" else "Servings") },
                supportingText = {
                    val hint = when {
                        unit == PortionUnit.SERVINGS && servingSize != null ->
                            "1 serving = ${servingSize.roundToInt()} g${food.servingLabel?.let { " ($it)" }.orEmpty()}"

                        grams != null -> "${grams.roundToInt()} g"
                        else -> "Enter an amount"
                    }
                    Text(hint)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = grams == null,
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { option ->
                    FilterChip(
                        selected = meal == option,
                        onClick = { meal = option },
                        label = { Text(option.label) },
                    )
                }
            }

            preview?.let {
                Text(
                    text = "${it.kcalRounded} kcal · P ${it.proteinG.roundToInt()}g · " +
                        "C ${it.carbsG.roundToInt()}g · F ${it.fatG.roundToInt()}g",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Button(
                onClick = {
                    val value = grams ?: return@Button
                    val label = if (unit == PortionUnit.SERVINGS && servingSize != null) {
                        "$amount × serving"
                    } else {
                        "${value.roundToInt()} g"
                    }
                    onConfirm(value, label, meal)
                },
                enabled = grams != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log it")
            }
        }
    }
}
