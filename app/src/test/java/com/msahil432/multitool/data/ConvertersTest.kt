package com.msahil432.multitool.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Room [Converters] type conversions for domain enums.
 */
class ConvertersTest {

  private lateinit var converters: Converters

  @Before
  fun setUp() {
    converters = Converters()
  }

  @Test
  fun `DeletionMode conversion roundtrip`() {
    for (mode in DeletionMode.entries) {
      val str = converters.fromDeletionMode(mode)
      val converted = converters.toDeletionMode(str)
      assertEquals(mode, converted)
    }
  }

  @Test
  fun `ActionStatus conversion roundtrip`() {
    for (status in ActionStatus.entries) {
      val str = converters.fromActionStatus(status)
      val converted = converters.toActionStatus(str)
      assertEquals(status, converted)
    }
  }

  @Test
  fun `LogAction conversion roundtrip`() {
    for (action in LogAction.entries) {
      val str = converters.fromLogAction(action)
      val converted = converters.toLogAction(str)
      assertEquals(action, converted)
    }
  }

  @Test
  fun `UnlockType conversion roundtrip`() {
    for (type in UnlockType.entries) {
      val str = converters.fromUnlockType(type)
      val converted = converters.toUnlockType(str)
      assertEquals(type, converted)
    }
  }

  @Test
  fun `TimelineEventType conversion roundtrip`() {
    for (type in TimelineEventType.entries) {
      val str = converters.fromTimelineEventType(type)
      val converted = converters.toTimelineEventType(str)
      assertEquals(type, converted)
    }
  }

  @Test
  fun `BlockRuleType conversion roundtrip`() {
    for (type in BlockRuleType.entries) {
      val str = converters.fromBlockRuleType(type)
      val converted = converters.toBlockRuleType(str)
      assertEquals(type, converted)
    }
  }

  @Test
  fun `BrowsingKind conversion roundtrip`() {
    for (kind in BrowsingKind.entries) {
      val str = converters.fromBrowsingKind(kind)
      val converted = converters.toBrowsingKind(str)
      assertEquals(kind, converted)
    }
  }
}
