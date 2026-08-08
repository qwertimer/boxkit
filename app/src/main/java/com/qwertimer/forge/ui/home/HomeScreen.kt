package com.qwertimer.forge.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.ui.common.MacroSummary
import com.qwertimer.forge.ui.common.StatTile
import java.time.format.DateTimeFormatter

private val TITLE_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onTrainClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(state.date.format(TITLE_FORMAT)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MacroSummary(consumed = state.totals.consumed, target = state.totals.target)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Training", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = state.trainingHeadline,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.trainingDetail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.plan?.status == PlanStatus.PENDING) {
                        Button(onClick = onTrainClick, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null)
                            Text("  Start session")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = "${state.stats?.currentStreak ?: 0}",
                    label = "Day streak",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${state.stats?.compliancePercent ?: 0}%",
                    label = "30-day compliance",
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("  Scan a barcode")
            }
        }
    }
}
