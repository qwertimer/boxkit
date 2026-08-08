package com.qwertimer.forge.enforce

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qwertimer.forge.MainActivity
import com.qwertimer.forge.domain.model.PlanStatus
import com.qwertimer.forge.ui.theme.ForgeTheme
import com.qwertimer.forge.ui.workout.SkipDialog
import com.qwertimer.forge.ui.workout.WorkoutViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The last line of enforcement.
 *
 * Shown over the lock screen when the escalation runs out of patience. Back does not dismiss it —
 * the only exits are logging the session or recording an explicit reason for skipping. It is
 * deliberately awkward: an escape hatch that costs nothing gets used every time.
 */
@AndroidEntryPoint
class EnforcementActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Toast.makeText(
                        this@EnforcementActivity,
                        "Log the session or record a skip reason.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )

        setContent {
            ForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    EnforcementContent(
                        onOpenApp = {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .setAction(MainActivity.ACTION_OPEN_WORKOUT)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            dismiss()
                        },
                        onResolved = ::dismiss,
                    )
                }
            }
        }
    }

    private fun dismiss() {
        Notifier(applicationContext).clear()
        finish()
    }
}

@Composable
private fun EnforcementContent(
    onOpenApp: () -> Unit,
    onResolved: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var skipDialog by remember { mutableStateOf<Long?>(null) }
    val plan = state.scheduled

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "You have not trained today",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        val streak = stats?.currentStreak ?: 0
        Text(
            text = when {
                plan == null -> "No session scheduled. You can close this."
                streak > 0 -> "Your $streak-day streak ends at midnight unless this gets logged."
                else -> "There is a session waiting. It takes ${plan.estimatedMinutes} minutes."
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        plan?.let {
            Text(
                text = "${it.focus.label} · ${it.blocks.size} movements · ~${it.estimatedMinutes} min",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = it.blocks.joinToString("\n") { block ->
                    "• ${block.exercise.name} — ${block.prescription}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (plan == null || plan.status != PlanStatus.PENDING) {
            Button(onClick = onResolved, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            return@Column
        }

        Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
            Text("Open the session")
        }
        OutlinedButton(
            onClick = {
                viewModel.markComplete(plan.id)
                onResolved()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("I already did it — mark complete") }

        TextButton(onClick = { skipDialog = plan.id }, modifier = Modifier.fillMaxWidth()) {
            Text("I'm skipping today")
        }
    }

    skipDialog?.let { planId ->
        SkipDialog(
            onDismiss = { skipDialog = null },
            onConfirm = { reason ->
                viewModel.skip(planId, reason)
                skipDialog = null
                onResolved()
            },
        )
    }
}
