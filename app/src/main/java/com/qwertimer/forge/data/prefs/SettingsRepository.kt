package com.qwertimer.forge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qwertimer.forge.domain.model.FitnessLevel
import com.qwertimer.forge.domain.model.Macros
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    val kcalTarget: Double = 2200.0,
    val proteinTargetG: Double = 150.0,
    val carbsTargetG: Double = 220.0,
    val fatTargetG: Double = 70.0,
    val trainingDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
    ),
    val reminderTime: LocalTime = LocalTime.of(18, 0),
    val nagIntervalMinutes: Int = 20,
    val maxNags: Int = 4,
    /** After the nags run out, take over the screen until the session is logged or skipped. */
    val fullScreenEnforcement: Boolean = true,
    val fitnessLevel: FitnessLevel = FitnessLevel.INTERMEDIATE,
    val sessionMinutes: Int = 30,
    val allowHighImpact: Boolean = true,
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val aiFallbackEnabled: Boolean = true,
) {
    val macroTargets: Macros
        get() = Macros(kcalTarget, proteinTargetG, carbsTargetG, fatTargetG)

    val hasGeminiKey: Boolean get() = geminiApiKey.isNotBlank()

    /** AI lookups need both a key and the toggle; either missing means Open Food Facts only. */
    val aiAvailable: Boolean get() = aiFallbackEnabled && hasGeminiKey

    fun isTrainingDay(day: DayOfWeek): Boolean = day in trainingDays

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val KCAL = doublePreferencesKey("kcal_target")
        val PROTEIN = doublePreferencesKey("protein_target")
        val CARBS = doublePreferencesKey("carbs_target")
        val FAT = doublePreferencesKey("fat_target")
        val TRAINING_DAYS = stringSetPreferencesKey("training_days")
        val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val NAG_INTERVAL = intPreferencesKey("nag_interval_minutes")
        val MAX_NAGS = intPreferencesKey("max_nags")
        val FULL_SCREEN = booleanPreferencesKey("full_screen_enforcement")
        val LEVEL = stringPreferencesKey("fitness_level")
        val SESSION_MINUTES = intPreferencesKey("session_minutes")
        val HIGH_IMPACT = booleanPreferencesKey("allow_high_impact")
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val AI_FALLBACK = booleanPreferencesKey("ai_fallback_enabled")
    }

    val settings: Flow<UserSettings> = context.settingsStore.data
        .catch { error ->
            // A corrupt preferences file should not take the whole app down with it.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            val defaults = UserSettings()
            UserSettings(
                kcalTarget = prefs[Keys.KCAL] ?: defaults.kcalTarget,
                proteinTargetG = prefs[Keys.PROTEIN] ?: defaults.proteinTargetG,
                carbsTargetG = prefs[Keys.CARBS] ?: defaults.carbsTargetG,
                fatTargetG = prefs[Keys.FAT] ?: defaults.fatTargetG,
                trainingDays = prefs[Keys.TRAINING_DAYS]
                    ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                    ?.toSet()
                    ?: defaults.trainingDays,
                reminderTime = prefs[Keys.REMINDER_MINUTE_OF_DAY]
                    ?.let { LocalTime.of(it / 60, it % 60) }
                    ?: defaults.reminderTime,
                nagIntervalMinutes = prefs[Keys.NAG_INTERVAL] ?: defaults.nagIntervalMinutes,
                maxNags = prefs[Keys.MAX_NAGS] ?: defaults.maxNags,
                fullScreenEnforcement = prefs[Keys.FULL_SCREEN] ?: defaults.fullScreenEnforcement,
                fitnessLevel = prefs[Keys.LEVEL]
                    ?.let { runCatching { FitnessLevel.valueOf(it) }.getOrNull() }
                    ?: defaults.fitnessLevel,
                sessionMinutes = prefs[Keys.SESSION_MINUTES] ?: defaults.sessionMinutes,
                allowHighImpact = prefs[Keys.HIGH_IMPACT] ?: defaults.allowHighImpact,
                geminiApiKey = prefs[Keys.GEMINI_KEY] ?: defaults.geminiApiKey,
                geminiModel = prefs[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() }
                    ?: defaults.geminiModel,
                aiFallbackEnabled = prefs[Keys.AI_FALLBACK] ?: defaults.aiFallbackEnabled,
            )
        }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setMacroTargets(kcal: Double, protein: Double, carbs: Double, fat: Double) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.KCAL] = kcal.coerceIn(800.0, 8000.0)
            prefs[Keys.PROTEIN] = protein.coerceAtLeast(0.0)
            prefs[Keys.CARBS] = carbs.coerceAtLeast(0.0)
            prefs[Keys.FAT] = fat.coerceAtLeast(0.0)
        }
    }

    suspend fun setTrainingDays(days: Set<DayOfWeek>) {
        context.settingsStore.edit { it[Keys.TRAINING_DAYS] = days.map(DayOfWeek::name).toSet() }
    }

    suspend fun setReminderTime(time: LocalTime) {
        context.settingsStore.edit { it[Keys.REMINDER_MINUTE_OF_DAY] = time.hour * 60 + time.minute }
    }

    suspend fun setNagInterval(minutes: Int) {
        context.settingsStore.edit { it[Keys.NAG_INTERVAL] = minutes.coerceIn(5, 180) }
    }

    suspend fun setMaxNags(count: Int) {
        context.settingsStore.edit { it[Keys.MAX_NAGS] = count.coerceIn(0, 12) }
    }

    suspend fun setFullScreenEnforcement(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.FULL_SCREEN] = enabled }
    }

    suspend fun setFitnessLevel(level: FitnessLevel) {
        context.settingsStore.edit { it[Keys.LEVEL] = level.name }
    }

    suspend fun setSessionMinutes(minutes: Int) {
        context.settingsStore.edit { it[Keys.SESSION_MINUTES] = minutes.coerceIn(10, 90) }
    }

    suspend fun setAllowHighImpact(allow: Boolean) {
        context.settingsStore.edit { it[Keys.HIGH_IMPACT] = allow }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.settingsStore.edit { it[Keys.GEMINI_KEY] = key.trim() }
    }

    suspend fun setGeminiModel(model: String) {
        context.settingsStore.edit { it[Keys.GEMINI_MODEL] = model.trim() }
    }

    suspend fun setAiFallbackEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.AI_FALLBACK] = enabled }
    }
}
