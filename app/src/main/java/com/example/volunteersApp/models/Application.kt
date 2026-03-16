package com.example.volunteersApp.models

import com.google.firebase.firestore.DocumentId

/**
 * Data class representing a single application submitted by a volunteer for an event.
 *
 * This model is designed to be easily serialized and deserialized by Firestore.
 *
 * @property applicationId The unique ID of the application document in Firestore.
 *                       The @DocumentId annotation automatically populates this field
 *                       with the document's ID upon retrieval.
 * @property eventId The ID of the event the volunteer is applying for.
 * @property volunteerId The ID of the user (volunteer) who submitted the application.
 * @property volunteerName The name of the volunteer, denormalized for easy display.
 * @property status The current status of the application (e.g., "Pending", "Approved", "Rejected").
 * @property applicationDate The timestamp when the application was submitted.
 */
data class Application(
    @DocumentId
    val applicationId: String = "",
    val eventId: String = "",
    val volunteerId: String = "",
    val volunteerName: String = "", // Denormalized for easier display in lists
    val status: String = "Pending",
    val applicationDate: Long = System.currentTimeMillis()

)
