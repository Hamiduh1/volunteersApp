package com.example.volunteersApp.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * A centralized object for defining navigation routes and arguments.
 * This ensures consistency and type safety across the app.
 */
object AppDestinations {

    // --- View Applicants ---
    const val VIEW_APPLICANTS_ROUTE = "view_applicants"
    const val EVENT_ID_ARG = "eventId"
    const val EVENT_NAME_ARG = "eventName"

    // The full route definition for the NavHost
    val viewApplicantsRouteWithArgs =
        "$VIEW_APPLICANTS_ROUTE/{$EVENT_ID_ARG}?$EVENT_NAME_ARG={$EVENT_NAME_ARG}"

    // The list of arguments for the NavHost entry
    val viewApplicantsArguments = listOf(
        navArgument(EVENT_ID_ARG) { type = NavType.StringType },
        navArgument(EVENT_NAME_ARG) {
            type = NavType.StringType
            nullable = true // eventName is optional
        }
    )

    // --- Other Routes in your app would go here ---
    // const val MARKETPLACE_ROUTE = "marketplace"
    // const val JOKES_ROUTE = "jokes"
    // ...etc.
}
