package com.qwertimer.forge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Ember = Color(0xFFB4501B)
private val EmberLight = Color(0xFFFFDBCA)
private val Steel = Color(0xFF5B5F5F)
private val Anvil = Color(0xFF17110D)

private val LightScheme = lightColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = EmberLight,
    onPrimaryContainer = Color(0xFF3A1300),
    secondary = Steel,
    background = Color(0xFFFBF8F6),
    surface = Color(0xFFFBF8F6),
    surfaceVariant = Color(0xFFF3DFD4),
    error = Color(0xFFBA1A1A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB693),
    onPrimary = Color(0xFF5D1900),
    primaryContainer = Color(0xFF832500),
    onPrimaryContainer = EmberLight,
    secondary = Color(0xFFC4C7C7),
    background = Anvil,
    surface = Anvil,
    surfaceVariant = Color(0xFF52443C),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You, where the device supports it. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
