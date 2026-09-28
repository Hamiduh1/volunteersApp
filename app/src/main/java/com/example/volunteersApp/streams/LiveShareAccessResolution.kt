package com.example.volunteersApp.streams

data class LiveShareAccessResolution(
    val valid: Boolean = false,
    val sessionId: String = "",
    val hostId: String = "",
    val status: String = "",
    val title: String = "",
    val replayReady: Boolean = false,
    val purpose: String = "",
)
