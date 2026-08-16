package com.msahil432.multitool.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msahil432.multitool.data.TimelineEvent
import com.msahil432.multitool.data.UsageDailyStat
import com.msahil432.multitool.data.UsageRepository
import com.msahil432.multitool.util.UsageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppMeta(
  val label: String,
  val icon: Bitmap? = null
)

class UsageViewModel(
  private val repository: UsageRepository,
  private val context: Context
) : ViewModel() {

  private val _isUsageAccessGranted = MutableStateFlow(UsageAccess.isGranted(context))
  val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

  val totalScreenTimeToday: StateFlow<Long> = repository.totalScreenTimeToday()
    .map { it ?: 0L }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

  val perApp: StateFlow<List<UsageDailyStat>> = repository.todayStats()
    .map { list -> list.sortedByDescending { it.foregroundMillis } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val unlocksToday: StateFlow<Int> = repository.unlocksToday()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  val timeline: StateFlow<List<TimelineEvent>> = repository.timelineToday()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _appMetaCache = MutableStateFlow<Map<String, AppMeta>>(emptyMap())
  val appMetaCache: StateFlow<Map<String, AppMeta>> = _appMetaCache.asStateFlow()

  private val _isLoaded = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoaded.asStateFlow().map { !it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  init {
    viewModelScope.launch {
      // Collect package names to prefetch app labels and icons
      combine(perApp, timeline) { stats, events ->
        val pkgs = mutableSetOf<String>()
        stats.forEach { pkgs.add(it.packageName) }
        events.forEach { pkgs.add(it.packageName) }
        pkgs
      }.collect { packages ->
        resolveAndCacheMetadata(packages)
        _isLoaded.value = true
      }
    }
  }

  fun refreshPermission() {
    _isUsageAccessGranted.value = UsageAccess.isGranted(context)
  }

  private suspend fun resolveAndCacheMetadata(packages: Set<String>) {
    withContext(Dispatchers.IO) {
      val currentCache = _appMetaCache.value.toMutableMap()
      var updated = false
      val pm = context.packageManager

      for (pkg in packages) {
        if (pkg.isBlank() || currentCache.containsKey(pkg)) continue
        val meta = loadAppMeta(pm, pkg)
        currentCache[pkg] = meta
        updated = true
      }

      if (updated) {
        _appMetaCache.value = currentCache
      }
    }
  }

  private fun loadAppMeta(pm: PackageManager, packageName: String): AppMeta {
    return try {
      val appInfo = pm.getApplicationInfo(packageName, 0)
      val label = pm.getApplicationLabel(appInfo).toString()
      val icon = pm.getApplicationIcon(appInfo).toBitmapOrNull()
      AppMeta(label = label, icon = icon)
    } catch (_: Exception) {
      AppMeta(label = packageName, icon = null)
    }
  }

  private fun Drawable.toBitmapOrNull(): Bitmap? {
    return try {
      if (this is BitmapDrawable && this.bitmap != null) {
        this.bitmap
      } else {
        val width = if (intrinsicWidth > 0) intrinsicWidth else 96
        val height = if (intrinsicHeight > 0) intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap
      }
    } catch (_: Exception) {
      null
    }
  }

  class Factory(
    private val repository: UsageRepository,
    private val context: Context
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(UsageViewModel::class.java)) {
        return UsageViewModel(repository, context) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}
