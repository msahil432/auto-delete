package com.msahil432.multitool.downloader

/**
 * Sealed hierarchy representing flattened entries in the detected media candidate list,
 * enabling visual group headers when multiple candidates share the same suggested file name.
 */
sealed class MediaListEntry {
    /** Header row shown when 2 or more detected candidates share the same suggested filename. */
    data class GroupHeader(val fileName: String, val count: Int) : MediaListEntry()

    /** Standard candidate item, optionally indented when grouped under a [GroupHeader]. */
    data class Item(val media: DetectedMedia, val isGroupChild: Boolean) : MediaListEntry()
}
