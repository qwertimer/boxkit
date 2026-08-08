package com.qwertimer.forge.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qwertimer.forge.ui.common.StatTile
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Stats") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${state.stats.currentStreak}", "Current streak", Modifier.weight(1f))
                StatTile("${state.stats.longestStreak}", "Longest streak", Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${state.stats.compliancePercent}%", "Compliance", Modifier.weight(1f))
                StatTile("${state.stats.completedLast30}", "Done · 30d", Modifier.weight(1f))
            }

            // The uncomfortable half of the picture, deliberately given equal billing.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = "${state.stats.skippedLast30}",
                    label = "Skipped · 30d",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiaryContainer,
                )
                StatTile(
                    value = "${state.stats.missedLast30}",
                    label = "Missed · 30d",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.errorContainer,
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Calories, last 14 days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.kcalHistory.isEmpty()) {
                        Text(
                            "Nothing logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        KcalBars(
                            values = state.kcalHistory,
                            target = state.kcalTarget,
                            barColor = MaterialTheme.colorScheme.primary,
                            overColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        )
                        Text(
                            "Average ${state.averageKcal.roundToInt()} kcal · target ${state.kcalTarget.roundToInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A deliberately plain bar chart. Bars over the target are drawn in the error colour so the shape
 * of a bad week is visible at a glance without reading a single number.
 */
@Composable
private fun KcalBars(
    values: List<Double>,
    target: Double,
    barColor: Color,
    overColor: Color,
    modifier: Modifier = Modifier,
) {
    val ceiling = maxOf(values.maxOrNull() ?: 0.0, target) * 1.1
    Canvas(modifier) {
        if (ceiling <= 0.0 || values.isEmpty()) return@Canvas
        val gap = size.width * 0.02f
        val barWidth = (size.width - gap * (values.size - 1)) / values.size

        val targetY = size.height * (1f - (target / ceiling).toFloat())
        drawLine(
            color = barColor.copy(alpha = 0.4f),
            start = Offset(0f, targetY),
            end = Offset(size.width, targetY),
            strokeWidth = 2f,
        )

        values.forEachIndexed { index, value ->
            val height = size.height * (value / ceiling).toFloat()
            drawRect(
                color = if (value > target) overColor else barColor,
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
            )
        }
    }
}
