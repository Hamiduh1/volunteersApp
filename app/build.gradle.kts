import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization) // For KotlinX Serialization
    alias(libs.plugins.androidx.navigation.safeargs) // Ensure this alias is correctly defined in libs.versions.toml

      //  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" // Check for latest version


    }


android {
    namespace = "com.example.volunteersApp"
    compileSdk = 35
        // Using 34 as a stable SDK, 35 is often beta. Adjust if 35 is specifically needed and stable.

    defaultConfig {
        applicationId = "com.LVCA.volunteersApp"
        minSdk = 25
        targetSdk = 35 // Match compileSdk for consistency with stable releases
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Access API KEY from local.properties

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            try {
                FileInputStream(localPropertiesFile).use { fis -> // Use try-with-resources
                    localProperties.load(fis)
                }
            } catch (e: Exception) {
                println("Warning: Could not load local.properties: ${e.message}")
            }
        }

        val mapsApiKeyFromLocalProps = localProperties.getProperty("MAPS_API_KEY", "")
        // Ensure buildFeatures.buildConfig is true for this to work
        buildConfigField("String", "MAPS_API_KEY_BUILDCONFIG", "\"$mapsApiKeyFromLocalProps\"")

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKeyFromLocalProps
    }

    buildTypes {
        release {
            isMinifyEnabled = true // This should resolve if AGP is correct
            isShrinkResources = true // This should resolve if AGP is correct
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            ) // This should resolve if AGP is correct
        }
        // debug {} // You can also define debug specific settings if needed
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    kotlinOptions { // Or just kotlin {} with jvmToolchain(17)
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjvm-default=all")
        // Add this for each experimental/unstable API you want to opt into module-wide
        freeCompilerArgs += "-Xopt-in=androidx.media3.common.util.UnstableApi"

    }

    buildFeatures {
        viewBinding = true
        buildConfig = true // Crucial for buildConfigField to work

    }

    lint {
        baseline = file("lint-baseline.xml")
    }

}
dependencies {



    implementation(platform(libs.firebase.bom))

    // AndroidX & Google Material
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.fragment)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.annotation)

    // Navigation
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // Firebase
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.inappmessaging.display)
    implementation(libs.firebase.config)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.dataconnect) // Ensure this is covered by BoM or specify version if needed

    // Google Play Services
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.identity)
    implementation(libs.play.services.auth.v2130) // Make sure this is the single play-services-auth version you intend to use
    implementation(libs.gms.play.services.maps.v1920)
    implementation(libs.play.services.location)



    implementation(libs.glide) // Check for the latest version
    // annotationProcessor "com.github.bumptech.glide:compiler:4.12.0" // If using Java
    // kapt "com.github.bumptech.glide:compiler:4.12.0" // If using Kotlin with kapt
    // Image Loading
    implementation(libs.picasso)
    //implementation(libs.glide)

    // Other UI
    implementation(libs.hdodenhof.circleimageview)

    // KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)

    // Third-party
    implementation(libs.facebook.login.v1600)

    // Media & Sceneform (if used)
    implementation(libs.media3.common)
    implementation(libs.scenecore) // Check if you have both scenecore and androidx.scenecore, might only need one
    implementation(libs.impress)
    implementation(libs.androidx.scenecore)
    implementation(libs.firebase.crashlytics.buildtools) // This or libs.scenecore

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
