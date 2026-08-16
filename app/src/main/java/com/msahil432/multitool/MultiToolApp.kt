package com.msahil432.multitool

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.tracking.UsageCollectorWorker

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MultiToolApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "multi_tool_db" // DB file name kept as-is (see 01-rename-package.md decision)
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()


        UsageCollectorWorker.schedule(this)

        val settingsRepo = com.msahil432.multitool.data.SettingsRepository(dataStore)
        com.msahil432.multitool.blocking.StrictModeController.init(this, settingsRepo)
    }
}
