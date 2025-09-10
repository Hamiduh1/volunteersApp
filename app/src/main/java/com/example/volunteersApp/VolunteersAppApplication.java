package com.example.volunteersApp;

import android.app.Application;
import android.util.Log;

// Firebase Core
import com.google.firebase.FirebaseApp;

// Optional: Firebase Crashlytics (uncomment if you add the dependency)
// import com.google.firebase.crashlytics.FirebaseCrashlytics;
// import com.example.volunteersApp.BuildConfig; // Needed if you use BuildConfig for Crashlytics

public class VolunteersAppApplication extends Application {

    private static final String TAG = "VolunteersApp"; // Tag for logging

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VolunteersAppApplication onCreate called");

        // Initialize Firebase
        // This is often done automatically if you have google-services.json and the plugin,
        // but explicitly calling it ensures it's done and can be useful for more complex setups.
        FirebaseApp.initializeApp(this);
        Log.i(TAG, "FirebaseApp initialized.");

        // Example: Initialize Firebase Crashlytics (if you're using it)
        // Ensure you have the dependency: implementation(platform("com.google.firebase:firebase-bom:VERSION"))
        //                                implementation("com.google.firebase:firebase-crashlytics-ktx") // or non-ktx
        //
        // FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        // By default, collection is enabled. You might want to disable it for debug builds.
        // crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        //
        // if (!BuildConfig.DEBUG) {
        //    Log.i(TAG, "Firebase Crashlytics collection enabled for release builds.");
        // } else {
        //    Log.i(TAG, "Firebase Crashlytics collection disabled for debug builds.");
        // }

        // Other application-wide initializations can go here.

        Log.i(TAG, "Application common initializations complete.");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "VolunteersAppApplication onTerminate called");
        // This method is primarily for debugging and might not always be called on production devices.
        // Release resources here if absolutely necessary and not handled elsewhere.
    }
}