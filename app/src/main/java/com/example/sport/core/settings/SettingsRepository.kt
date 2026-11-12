package com.example.sport.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.sport.core.model.EffortScale
import com.example.sport.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Preferenze dell'utente. I valori predefiniti sono quelli di una palestra italiana. */
@Serializable
data class UserSettings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val effortScale: EffortScale = EffortScale.RPE,
)

/**
 * Impostazioni su DataStore (file `user_settings.preferences_pb`): lettura come flusso, scrittura
 * transazionale. Un file illeggibile non blocca l'app: si riparte dai valori predefiniti.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            UserSettings(
                weightUnit = preferences[WEIGHT_UNIT].toEnum(WeightUnit.KG),
                effortScale = preferences[EFFORT_SCALE].toEnum(EffortScale.RPE),
            )
        }
        .distinctUntilChanged()

    suspend fun current(): UserSettings = settings.first()

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[WEIGHT_UNIT] = unit.name }
    }

    suspend fun setEffortScale(scale: EffortScale) {
        dataStore.edit { it[EFFORT_SCALE] = scale.name }
    }

    /** Ripristino da backup: sostituisce tutte le preferenze in un'unica scrittura. */
    suspend fun replace(settings: UserSettings) {
        dataStore.edit {
            it[WEIGHT_UNIT] = settings.weightUnit.name
            it[EFFORT_SCALE] = settings.effortScale.name
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val EFFORT_SCALE = stringPreferencesKey("effort_scale")
    }
}
