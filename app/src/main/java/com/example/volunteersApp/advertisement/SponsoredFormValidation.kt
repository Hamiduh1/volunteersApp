package com.example.volunteersApp.advertisement

import android.net.Uri

internal const val SPONSORED_TITLE_MAX_LENGTH = 120
internal const val SPONSORED_DESCRIPTION_MAX_LENGTH = 2_000
internal const val SPONSORED_TARGET_URL_MAX_LENGTH = 2_048
internal const val SPONSORED_MEDIA_MAX_ITEMS = 8

internal fun countDigits(s: String): Int = s.count { it.isDigit() }

internal fun sponsoredListingValidationError(title: String, description: String): String? = when {
    title.trim().length > SPONSORED_TITLE_MAX_LENGTH ->
        "Title must be $SPONSORED_TITLE_MAX_LENGTH characters or fewer."
    description.trim().length > SPONSORED_DESCRIPTION_MAX_LENGTH ->
        "Description must be $SPONSORED_DESCRIPTION_MAX_LENGTH characters or fewer."
    else -> null
}

internal fun sponsoredMediaValidationError(mediaCount: Int, mediaTypes: Collection<String>): String? = when {
    mediaCount > SPONSORED_MEDIA_MAX_ITEMS ->
        "Add up to $SPONSORED_MEDIA_MAX_ITEMS media items."
    mediaTypes.any { it.lowercase() !in setOf("image", "video", "document") } ->
        "Only images, videos, and documents can be attached."
    else -> null
}

/** Trim; if no scheme, prepend https:// */
internal fun normalizeTargetUrl(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    return if (!t.contains("://")) "https://$t" else t
}

/** http(s) with a non-blank, sane host (blocks obvious junk / whitespace hosts). */
internal fun isPublishableHttpUrl(url: String): Boolean {
    val trimmedUrl = url.trim()
    if (trimmedUrl.length > SPONSORED_TARGET_URL_MAX_LENGTH) return false
    val u = Uri.parse(trimmedUrl)
    val scheme = u.scheme?.lowercase() ?: return false
    if (scheme !in setOf("http", "https")) return false
    val host = u.host?.trim().orEmpty()
    if (host.isEmpty()) return false
    if (host.any { it.isWhitespace() }) return false
    if (host.startsWith('.') || host.endsWith('.')) return false
    if (host.length > 253) return false
    return true
}

/** Optional advertiser phone: blank OK, else at least 7 digits */
internal fun validateOptionalAdPhone(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return if (countDigits(t) >= 7) null else "Phone must include at least 7 digits, or leave it blank."
}

internal fun validateOptionalGaragePhone(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return if (countDigits(t) >= 7) null else "Phone must include at least 7 digits, or leave it blank."
}

internal fun validateOptionalEmail(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return if (t.contains('@') && t.contains('.')) null else "Enter a valid email or leave it blank."
}

/** Blank → null; invalid non-blank → error message */
internal fun parseOptionalLatitude(raw: String): Pair<Double?, String?> {
    val t = raw.trim()
    if (t.isEmpty()) return null to null
    val v = t.toDoubleOrNull() ?: return null to "Latitude is not a valid number."
    return if (v in -90.0..90.0) v to null else null to "Latitude must be between -90 and 90."
}

internal fun parseOptionalLongitude(raw: String): Pair<Double?, String?> {
    val t = raw.trim()
    if (t.isEmpty()) return null to null
    val v = t.toDoubleOrNull() ?: return null to "Longitude is not a valid number."
    return if (v in -180.0..180.0) v to null else null to "Longitude must be between -180 and 180."
}
