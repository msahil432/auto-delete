package com.msahil432.multitool.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

  @Test
  fun `toHms formats zero and negative durations as 0m`() {
    assertEquals("0m", 0L.toHms())
    assertEquals("0m", (-500L).toHms())
  }

  @Test
  fun `toHms formats seconds correctly`() {
    assertEquals("30s", 30_000L.toHms())
    assertEquals("59s", 59_000L.toHms())
  }

  @Test
  fun `toHms formats minutes correctly`() {
    assertEquals("1m", 60_000L.toHms())
    assertEquals("45m", (45 * 60 * 1000L).toHms())
  }

  @Test
  fun `toHms formats hours and minutes correctly`() {
    assertEquals("1h", (60 * 60 * 1000L).toHms())
    assertEquals("2h 15m", (2 * 3600 * 1000L + 15 * 60 * 1000L).toHms())
    assertEquals("5h 30m", (5 * 3600 * 1000L + 30 * 60 * 1000L).toHms())
  }
}
