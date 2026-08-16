package com.msahil432.multitool.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.msahil432.multitool.MultiToolApp
import com.msahil432.multitool.blocking.BlockEngine
import com.msahil432.multitool.data.*
import com.msahil432.multitool.notification.NotificationDigestScheduler
import com.msahil432.multitool.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MultiToolNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var blockingRepo: BlockingRepository
    private lateinit var blockEngine: BlockEngine

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as MultiToolApp
        val db = app.database
        notificationRepo = NotificationRepository(db.notificationDao())
        settingsRepo = SettingsRepository(applicationContext.dataStore)
        blockingRepo = BlockingRepository(db.blockingDao())
        val usageRepo = UsageRepository(db.usageDao())
        blockEngine = BlockEngine(blockingRepo, usageRepo)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Never intercept self or ongoing system foreground notices (calls, media playback, etc.)
        if (pkg == packageName) return
        if (sbn.isOngoing) return

        serviceScope.launch {
            if (isRestrictedNow(pkg)) {
                val title = extractTitle(sbn)
                val text = extractText(sbn)
                val vaulted = VaultedNotification(
                    packageName = pkg,
                    title = title,
                    text = text,
                    postedAt = sbn.postTime,
                    delivered = false
                )
                notificationRepo.vault(vaulted)
                cancelNotification(sbn.key)

                NotificationDigestScheduler.scheduleDigestIfUpcoming(
                    applicationContext,
                    blockingRepo,
                    blockEngine
                )
            }
        }
    }

    private suspend fun isRestrictedNow(pkg: String): Boolean {
        val enabled = settingsRepo.notificationVaultEnabled.first()
        if (!enabled) return false

        val blockedPkgs = settingsRepo.notificationBlockedPackages.first()
        val inDedicatedList = blockedPkgs.contains(pkg)

        val groups = blockingRepo.enabledGroupsContaining(pkg)
        for (group in groups) {
            val rules = blockingRepo.enabledRules(group.id)
            for (rule in rules) {
                if (rule.type == BlockRuleType.SCHEDULE && blockEngine.nowWithinSchedule(rule)) {
                    return true
                }
            }
        }

        if (inDedicatedList) {
            val allGroups = blockingRepo.groups().first()
            val anyScheduleRuleExists = allGroups.filter { it.enabled }.any { group ->
                blockingRepo.enabledRules(group.id).any { it.type == BlockRuleType.SCHEDULE }
            }
            if (anyScheduleRuleExists) {
                return allGroups.filter { it.enabled }.any { group ->
                    blockingRepo.enabledRules(group.id).any { rule ->
                        rule.type == BlockRuleType.SCHEDULE && blockEngine.nowWithinSchedule(rule)
                    }
                }
            }
            return true
        }

        return false
    }

    private fun extractTitle(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        return title?.toString()
    }

    private fun extractText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        return text?.toString()
    }
}
