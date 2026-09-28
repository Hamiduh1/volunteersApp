package com.example.volunteersApp

import android.app.Application
import android.util.Log
import com.example.volunteersApp.initializers.AppCheckInitializer
import com.example.volunteersApp.wallet.WalletSecurityService
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp

class VolunteersApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install the security provider
        try {
            ProviderInstaller.installIfNeeded(applicationContext)
        } catch (e: GooglePlayServicesRepairableException) {
            Log.e("VolunteersApplication", "Play Services repairable error", e)
        } catch (e: GooglePlayServicesNotAvailableException) {
            Log.e("VolunteersApplication", "Play Services not available", e)
        }

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // --- THIS IS THE FIX ---
        // Call the AppCheckInitializer. Gradle will automatically provide the
        // correct version (debug or release) based on the build type.
        AppCheckInitializer.install(this)

        // Wallet security session should expire whenever app moves to background/inactive.
        WalletSecurityService.installProcessLifecycleObserver(this)
    }
}
