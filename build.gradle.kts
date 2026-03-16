// Top-level build file where you can add configuration options common to all sub-projects/modules.



plugins {
    alias(libs.plugins.google.devtools.ksp) apply false   // declare KSP here
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.kotlin.android) apply false

    // *** THIS IS THE CRITICAL LINE TO ADD ***
    // This declares that the Safe Args plugin is available for sub-modules to use.
    alias(libs.plugins.androidx.navigation.safeargs.kotlin) apply false



    // This declares the Compose compiler plugin is available for sub-modules.
    // Your existing line for this is also fine, but using the alias is cleaner.
   // alias(libs.plugins.kotlin.compose) apply false

}
