package com.example.volunteersApp.initializers

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * This is the RELEASE version of the AppCheckInitializer.
 * It will only be included in release builds.
 */
object AppCheckInitializer {
    fun install(context: Context) {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
