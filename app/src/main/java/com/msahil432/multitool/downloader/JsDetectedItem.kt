package com.msahil432.multitool.downloader

import com.squareup.moshi.JsonClass

/**
 * JSON data model deserialized from JavaScript DOM scan payloads.
 */
@JsonClass(generateAdapter = true)
data class JsDetectedItem(
    val url: String,
    val mimeType: String? = null,
    val strategy: String? = null,
    val mediaType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val thumbnail: String? = null
)

/**
 * JSON data model deserialized from JavaScript canvas candidate detection payloads.
 */
@JsonClass(generateAdapter = true)
data class JsCanvasCandidate(
    val type: String,
    val selector: String,
    val width: Int? = null,
    val height: Int? = null,
    val url: String? = null,
    val elementIndex: Int? = null
)
