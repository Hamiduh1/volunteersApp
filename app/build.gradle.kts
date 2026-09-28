import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.firebase.crashlytics)
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

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    try {
        FileInputStream(keystorePropertiesFile).use { fis ->
            keystoreProperties.load(fis)
        }
    } catch (e: Exception) {
        println("Warning: Could not load keystore.properties: ${e.message}")
    }
}

fun propValue(props: Properties, key: String): String = props.getProperty(key)?.trim().orEmpty()

fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

val releaseStoreFilePath = propValue(keystoreProperties, "storeFile")
val releaseStorePassword = propValue(keystoreProperties, "storePassword")
val releaseKeyAlias = propValue(keystoreProperties, "keyAlias")
val releaseKeyPassword = propValue(keystoreProperties, "keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

val stripePublishableKey = firstNonBlank(
    localProperties.getProperty("STRIPE_PUBLISHABLE_KEY"),
    localProperties.getProperty("stripe_publishable_key"),
    System.getenv("STRIPE_PUBLISHABLE_KEY")
)

android {
    namespace = "com.example.volunteersApp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.volunteersapp.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.0.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- Improvement 2: Set manifest placeholders here ---
        // This reads the key from the properties loaded at the top.
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias

                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println(
                    "Warning: release signing is not configured. " +
                        "Create keystore.properties to produce a signed release APK/AAB."
                )
            }
            if (stripePublishableKey.isBlank()) {
                throw GradleException(
                    "Missing Stripe publishable key for release build. " +
                        "Add STRIPE_PUBLISHABLE_KEY to local.properties or environment before generating the AAB."
                )
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField(
                "String",
                "STRIPE_PUBLISHABLE_KEY",
                "\"$stripePublishableKey\""
            )
            buildConfigField(
                "String",
                "AGORA_APP_ID",
                "\"${localProperties.getProperty("agora.appId", "")}\""
            )
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

        debug {
            isDebuggable = true
            buildConfigField(
                "String",
                "STRIPE_PUBLISHABLE_KEY",
                "\"$stripePublishableKey\""
            )
            buildConfigField(
                "String",
                "AGORA_APP_ID",
                "\"${localProperties.getProperty("agora.appId", "")}\""
            )
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
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("META-INF/LICENSE.md")
            excludes.add("META-INF/LICENSE-notice.md")

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
        dataBinding = false
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
}

dependencies {
    // BOMs (Bills of Materials)
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.compose.bom))
    implementation(platform(libs.kotlinx.coroutines.bom))
    androidTestImplementation(platform(libs.compose.bom))

    // Firebase
    implementation(libs.firebase.common.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.inappmessaging.display.ktx)
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.functions.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.mlkit.common)
    implementation(libs.firebase.dataconnect)
    implementation(libs.firebase.vertexai)

    // App Check
    implementation(libs.firebase.appcheck.ktx)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.playintegrity) // CORRECTED


    // AndroidX & Google Material
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.gridlayout)
    implementation(libs.annotation)

    implementation(libs.androidx.datastore.preferences)

    // Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.navigation.compose)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler) // CORRECTED

    // Jetpack Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime.saveable)
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

}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
}
