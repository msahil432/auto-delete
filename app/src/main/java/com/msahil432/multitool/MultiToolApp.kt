package com.msahil432.multitool

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.msahil432.multitool.data.AppDatabase
import com.msahil432.multitool.tracking.UsageCollectorWorker
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MultiToolApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()

        initSentry()

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

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isNotBlank()) {
            SentryAndroid.init(this) { options: SentryAndroidOptions ->
                options.dsn = dsn

                // Performance tracing — safe to sample 100% at our volume
                options.tracesSampleRate = 1.0

                // Session Replay (optional) — small free quota, useful at low volume
                options.sessionReplay.onErrorSampleRate = 1.0
                options.sessionReplay.sessionSampleRate = 0.1

                // Only enable debug logging locally, never in release
                options.isDebug = BuildConfig.DEBUG

                // Attaches screenshots on crash (optional, helps debugging)
                options.isAttachScreenshot = true
            }
        }
    }
}

