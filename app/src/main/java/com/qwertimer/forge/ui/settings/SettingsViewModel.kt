package com.qwertimer.forge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwertimer.forge.data.prefs.SettingsRepository
import com.qwertimer.forge.data.prefs.UserSettings
import com.qwertimer.forge.domain.model.FitnessLevel
import com.qwertimer.forge.enforce.EnforcementScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: EnforcementScheduler,
) : ViewModel() {

    val state: StateFlow<UserSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** Anything that changes when or whether we nag has to re-arm the pending alarm. */
    private fun updateAndReschedule(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            scheduler.scheduleNextReminder()
        }
    }

    fun setTargets(kcal: Double, protein: Double, carbs: Double, fat: Double) {
        viewModelScope.launch { settings.setMacroTargets(kcal, protein, carbs, fat) }
    }

    fun toggleTrainingDay(day: DayOfWeek) {
        updateAndReschedule {
            val current = settings.current().trainingDays
            settings.setTrainingDays(if (day in current) current - day else current + day)
        }
    }

    fun setReminderTime(time: LocalTime) = updateAndReschedule { settings.setReminderTime(time) }

    fun setNagInterval(minutes: Int) = updateAndReschedule { settings.setNagInterval(minutes) }

    fun setMaxNags(count: Int) = updateAndReschedule { settings.setMaxNags(count) }

    fun setFullScreenEnforcement(enabled: Boolean) =
        updateAndReschedule { settings.setFullScreenEnforcement(enabled) }

    fun setFitnessLevel(level: FitnessLevel) {
        viewModelScope.launch { settings.setFitnessLevel(level) }
    }

    fun setSessionMinutes(minutes: Int) {
        viewModelScope.launch { settings.setSessionMinutes(minutes) }
    }

    fun setAllowHighImpact(allow: Boolean) {
        viewModelScope.launch { settings.setAllowHighImpact(allow) }
    }

    fun setGeminiKey(key: String) {
        viewModelScope.launch { settings.setGeminiApiKey(key) }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch { settings.setGeminiModel(model) }
    }

    fun setAiFallback(enabled: Boolean) {
        viewModelScope.launch { settings.setAiFallbackEnabled(enabled) }
    }
}
