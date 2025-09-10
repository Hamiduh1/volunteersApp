package com.example.volunteersApp.models

// Assuming EventModel is defined (either as Kotlin data class or Java POJO)
// If EventModel is Java, its properties will be accessed via getters.
// If EventModel is Kotlin, its properties can be accessed directly.

data class EventWithVolunteerCount(
    val event: EventModel,
    val confirmedVolunteersCount: Int
)

