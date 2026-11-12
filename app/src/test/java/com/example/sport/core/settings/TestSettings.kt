package com.example.sport.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/** DataStore su un file nuovo (DataStore vuole una sola istanza per file): ogni test parte da zero. */
fun testDataStore(directory: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        File(directory, "settings-${UUID.randomUUID()}.preferences_pb")
    }

/** Impostazioni con i valori predefiniti. */
fun testSettingsRepository(directory: File): SettingsRepository = SettingsRepository(testDataStore(directory))
