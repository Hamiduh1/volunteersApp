package com.example.volunteersApp.models

enum class JobApplicationStatus {
    PENDING,
    VIEWED,
   SHORTLISTED,
    INTERVIEWING,
    OFFER_EXTENDED,
    ACCEPTED, // Volunteer accepted offer
    REJECTED_BY_EMPLOYER,
    REJECTED_BY_VOLUNTEER, // Volunteer declined offer
    WITHDRAWN // Volunteer withdrew application
 }