package com.msahil432.multitool.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FilterRule] matching logic and encoding/decoding functions.
 */
class FilterRuleTest {

  @Test
  fun `PREFIX rule matches start of filename case-insensitively`() {
    val rule = FilterRule(".trash", FilterMatchType.PREFIX)
    assertTrue(rule.matches(".trash_item.jpg"))
    assertTrue(rule.matches(".TRASH_item.jpg"))
    assertFalse(rule.matches("my_.trash_item.jpg"))
  }

  @Test
  fun `SUFFIX rule matches end of filename case-insensitively`() {
    val rule = FilterRule(".tmp", FilterMatchType.SUFFIX)
    assertTrue(rule.matches("cache_file.tmp"))
    assertTrue(rule.matches("CACHE_FILE.TMP"))
    assertFalse(rule.matches("cache_file.tmp.bak"))
  }

  @Test
  fun `CONTAINS rule matches substring anywhere in filename case-insensitively`() {
    val rule = FilterRule("backup", FilterMatchType.CONTAINS)
    assertTrue(rule.matches("my_backup_file.zip"))
    assertTrue(rule.matches("BACKUP.png"))
    assertTrue(rule.matches("file_backup"))
    assertFalse(rule.matches("my_normal_file.zip"))
  }

  @Test
  fun `DEFAULT_EXCLUSION_RULES contains expected default entries`() {
    assertEquals(4, DEFAULT_EXCLUSION_RULES.size)
    assertTrue(DEFAULT_EXCLUSION_RULES.any { it.pattern == ".trash" && it.matchType == FilterMatchType.PREFIX })
    assertTrue(DEFAULT_EXCLUSION_RULES.any { it.pattern == ".pending" && it.matchType == FilterMatchType.PREFIX })
    assertTrue(DEFAULT_EXCLUSION_RULES.any { it.pattern == ".nomedia" && it.matchType == FilterMatchType.PREFIX })
    assertTrue(DEFAULT_EXCLUSION_RULES.any { it.pattern == ".tmp" && it.matchType == FilterMatchType.SUFFIX })
  }

  @Test
  fun `encodeFilterRules produces semicolon-pipe formatted string`() {
    val rules = listOf(
      FilterRule(".trash", FilterMatchType.PREFIX),
      FilterRule(".tmp", FilterMatchType.SUFFIX)
    )
    val encoded = encodeFilterRules(rules)
    assertEquals(".trash|PREFIX;.tmp|SUFFIX", encoded)
  }

  @Test
  fun `decodeFilterRules parses pipe format correctly`() {
    val raw = ".trash|PREFIX;.tmp|SUFFIX;.nomedia|PREFIX"
    val decoded = decodeFilterRules(raw)
    assertEquals(3, decoded.size)
    assertEquals(FilterRule(".trash", FilterMatchType.PREFIX), decoded[0])
    assertEquals(FilterRule(".tmp", FilterMatchType.SUFFIX), decoded[1])
    assertEquals(FilterRule(".nomedia", FilterMatchType.PREFIX), decoded[2])
  }

  @Test
  fun `decodeFilterRules handles null or blank input`() {
    assertTrue(decodeFilterRules(null).isEmpty())
    assertTrue(decodeFilterRules("").isEmpty())
    assertTrue(decodeFilterRules("   ").isEmpty())
  }

  @Test
  fun `decodeFilterRules parses legacy JSON fallback format`() {
    val legacyJson = """[{"pattern":".trash","matchType":"PREFIX"},{"pattern":".tmp","matchType":"SUFFIX"}]"""
    val decoded = decodeFilterRules(legacyJson)
    assertEquals(2, decoded.size)
    assertEquals(FilterRule(".trash", FilterMatchType.PREFIX), decoded[0])
    assertEquals(FilterRule(".tmp", FilterMatchType.SUFFIX), decoded[1])
  }
}
