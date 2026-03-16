package com.example.volunteersApp.initializers

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * This is the DEBUG version of the AppCheckInitializer.
 * It will only be included in debug builds.
 */
object AppCheckInitializer {
    fun install(context: Context) {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
