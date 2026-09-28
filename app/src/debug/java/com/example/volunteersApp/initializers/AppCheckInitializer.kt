package com.example.volunteersApp.initializers

import android.content.Context
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Installs Firebase's debug provider so enforced services accept local builds. */
object AppCheckInitializer {
    fun install(context: Context) {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
            true,
        )
        Log.i(
            "AppCheckInitializer",
            "Debug App Check provider installed for ${context.packageName}. Register the Logcat debug token in Firebase."
        )
    }
}
