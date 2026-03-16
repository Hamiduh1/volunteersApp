import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget




plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
   // alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
    alias(libs.plugins.google.devtools.ksp)
}

// --- Improvement 1: Load properties once at the top level ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    try {
        FileInputStream(localPropertiesFile).use { fis ->
            localProperties.load(fis)
        }
    } catch (e: Exception) {
        println("Warning: Could not load local.properties: ${e.message}")
    }
}

android {
    namespace = "com.example.volunteersApp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.volunteersApp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- Improvement 2: Set manifest placeholders here ---
        // This reads the key from the properties loaded at the top.
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
    }

    buildTypes {
        // --- Improvement 3: Define build config fields for ALL build types ---
        all {
            // Stripe Publishable Key
            buildConfigField(
                "String",
                "STRIPE_PUBLISHABLE_KEY",
                "\"${localProperties.getProperty("STRIPE_PUBLISHABLE_KEY", "")}\""
            )

            // Agora App ID
            buildConfigField(
                "String",
                "AGORA_APP_ID",
                "\"${localProperties.getProperty("agora.appId", "")}\""
            )

            // Maps API Key (for use in Kotlin/Java code if needed)
            buildConfigField(
                "String",
                "MAPS_API_KEY_BUILDCONFIG",
                "\"${localProperties.getProperty("MAPS_API_KEY", "")}\""

            )

            buildConfigField(
                "String",
                "GEMINI_API_KEY",
                "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
            )

        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            // No specific overrides needed for debug at this time
        }
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("META-INF/LICENSE.md")
            excludes.add("META-INF/LICENSE-notice.md")

            // Kept remaining risky pickFirsts as requested, but the root cause is now fixed.
            pickFirsts.add("messages/JavaOptionBundle.properties")
            pickFirsts.add("messages/JavaErrorBundle.properties")
            pickFirsts.add("misc/registry.properties")
            pickFirsts.add("META-INF/extensions/compiler.xml")
            pickFirsts.add("META-INF/proguard/androidx-service.pro")
            pickFirsts.add("DebugProbesKt.bin")
            pickFirsts.add("kotlinManifest.properties")
            pickFirsts.add("messages/CoreBundle.properties")
            pickFirsts.add("messages/UtilBundle.properties")
            pickFirsts.add("messages/CoreDeprecatedMessagesBundle.properties")
            pickFirsts.add("messages/JavaPsiBundle.properties")
        }
        jniLibs {
            pickFirsts.add("lib/**/libaosl.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xjvm-default=all",
                "-Xopt-in=androidx.media3.common.util.UnstableApi"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        baseline = file("lint-baseline.xml")
    }


//Compose compiler is bundled inside the Kotlin compiler
//and activated automatically by AGP when you enable:
// so no need of kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
}

dependencies {
    // BOMs (Bills of Materials)
    implementation(platform(libs.firebase.bom))
    // BOMs (Bills of Materials)
   // implementation(platform(libs.firebase.bom.v3480)) // Use the latest BoM
    implementation(platform(libs.compose.bom))
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    androidTestImplementation(platform(libs.compose.bom))

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
    implementation(libs.mlkit.common)
    implementation(libs.firebase.dataconnect)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.firebase.vertexai.v1650)
    // FIX: Added Firebase App Check for security
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)


    // AndroidX & Google Material
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.fragment)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.gridlayout)
    implementation(libs.annotation)

    // Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.navigation.compose)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Jetpack Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.activity)
    implementation(libs.accompanist.swiperefresh)

    // Google Play Services, Identity, and Maps
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.identity)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)

    // Third-Party Libraries
    implementation(libs.glide)
    implementation(libs.picasso)
    implementation(libs.coil.compose)
    implementation(libs.hdodenhof.circleimageview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.facebook.login)
    implementation(libs.agora.rtc.full.sdk)
    implementation(libs.agora.rtm)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.sceneform)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.stripe.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    // REMOVED: implementation(libs.room.compiler.processing.testing) -> This was the root cause of the conflict.


}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")


}
