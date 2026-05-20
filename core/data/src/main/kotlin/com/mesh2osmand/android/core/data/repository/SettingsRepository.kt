package com.mesh2osmand.android.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val OSMAND_PACKAGE = stringPreferencesKey("osmand_package")
        val MESHTASTIC_PACKAGE = stringPreferencesKey("meshtastic_package")
    }

    val selectedOsmandPackage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.OSMAND_PACKAGE] ?: "net.osmand.plus"
    }

    val selectedMeshtasticPackage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[Keys.MESHTASTIC_PACKAGE] ?: "com.geeksville.mesh"
    }

    suspend fun setOsmandPackage(packageName: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.OSMAND_PACKAGE] = packageName
        }
    }

    suspend fun setMeshtasticPackage(packageName: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MESHTASTIC_PACKAGE] = packageName
        }
    }
}
