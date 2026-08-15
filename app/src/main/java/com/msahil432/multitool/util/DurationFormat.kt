package com.msahil432.multitool.util

import java.util.concurrent.TimeUnit

/**
 * Formats a duration in milliseconds to human-readable format, e.g. "2h 15m", "45m", "30s", "0m".
 */
fun Long.toHms(): String {
  if (this <= 0L) return "0m"

  val totalSeconds = this / 1000
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60

  return when {
    hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
    minutes > 0 -> "${minutes}m"
    seconds > 0 -> "${seconds}s"
    else -> "0m"
  }
}
