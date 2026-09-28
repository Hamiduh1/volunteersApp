package com.example.volunteersApp.initializers

import android.content.Context
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Installs Play Integrity for production Firebase App Check enforcement. */
object AppCheckInitializer {
    fun install(context: Context) {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
            true,
        )
        Log.i(
            "AppCheckInitializer",
            "Play Integrity App Check provider installed for ${context.packageName}."
        )
    }
}
