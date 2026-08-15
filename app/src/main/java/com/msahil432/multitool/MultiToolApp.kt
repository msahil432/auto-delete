package com.msahil432.multitool

import android.app.Application
import androidx.room.Room
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.data.MIGRATION_1_2
import com.msahil432.multitool.data.MIGRATION_2_3
import com.msahil432.multitool.data.MIGRATION_3_4
import com.msahil432.multitool.data.MIGRATION_4_5
import com.msahil432.multitool.data.MIGRATION_5_6
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }
}
