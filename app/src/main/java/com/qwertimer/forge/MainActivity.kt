package com.qwertimer.forge

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import com.qwertimer.forge.ui.nav.ForgeNavHost
import com.qwertimer.forge.ui.theme.ForgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var startOnWorkout by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startOnWorkout = intent?.action == ACTION_OPEN_WORKOUT

        setContent {
            ForgeTheme {
                RequestNotificationPermissionOnce()
                ForgeNavHost(startOnWorkout = startOnWorkout)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Launching from a reminder should land on the session, not wherever the app was left.
        if (intent.action == ACTION_OPEN_WORKOUT) startOnWorkout = true
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = "com.qwertimer.forge.action.OPEN_WORKOUT"
    }
}

/**
 * Notifications are the entire enforcement mechanism, so the app asks for the permission on first
 * launch rather than waiting for the first missed session to discover it never had it.
 */
@androidx.compose.runtime.Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
