package com.example.volunteersApp.date

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Normalizes dating profile photo fields from Firestore (supports legacy keys and shapes).
 */
internal fun normalizeDatingImageUrls(
    doc: DocumentSnapshot,
    fallback: List<String> = emptyList()
): List<String> {
    val urls = mutableListOf<String>()

    fun addValue(value: Any?) {
        when (value) {
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) urls.add(trimmed)
            }
            is List<*> -> value.forEach { item -> addValue(item) }
            is Map<*, *> -> {
                listOf("url", "downloadUrl", "imageUrl", "uri").forEach { key ->
                    (value[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let(urls::add)
                }
            }
        }
    }

    addValue(doc.get("imageUrls"))
    addValue(doc.get("images"))
    addValue(doc.get("photos"))
    addValue(doc.get("mediaUrls"))
    doc.getString("imageUrl")?.trim()?.takeIf { it.isNotEmpty() }?.let { urls.add(0, it) }

    if (urls.isEmpty()) {
        fallback.map { it.trim() }.filter { it.isNotEmpty() }.forEach(urls::add)
    }

    return urls.distinct()
}

internal fun DatingProfile.resolvedImageUrls(): List<String> =
    imageUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
