package com.msahil432.autodelete

import android.app.Application
import androidx.room.Room
import com.msahil432.autodelete.data.AppDatabase
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AutoDeleteApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "auto_delete_db"
        ).build()
    }
}
