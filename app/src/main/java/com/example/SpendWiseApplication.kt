package com.example

import android.app.Application
import com.example.data.local.SpendWiseDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.SpendWiseRepository

class SpendWiseApplication : Application() {
    val database: SpendWiseDatabase by lazy { SpendWiseDatabase.getInstance(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }
    val repository: SpendWiseRepository by lazy {
        SpendWiseRepository(
            database = database,
            settingsDataStore = settingsDataStore
        )
    }
}
