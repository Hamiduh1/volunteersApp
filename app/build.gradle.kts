import com.android.build.api.dsl.Packaging
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization) // For KotlinX Serialization
    alias(libs.plugins.androidx.navigation.safeargs)
   // alias(libs.plugins.google.devtools.ksp) // <<< Ensure KSP plugin is applied here if needed

}

android {
    namespace = "com.example.volunteersApp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.LVCA.volunteersApp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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

        val mapsApiKeyFromLocalProps = localProperties.getProperty("MAPS_API_KEY", "")
        buildConfigField("String", "MAPS_API_KEY_BUILDCONFIG", "\"$mapsApiKeyFromLocalProps\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKeyFromLocalProps
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


        packagingOptions {
            resources {
                pickFirsts.add("messages/JavaOptionBundle.properties")
                pickFirsts.add("messages/JavaErrorBundle.properties")
                pickFirsts.add("kotlin/reflect/reflect.kotlin_builtins")
                pickFirsts.add("kotlin/collections/collections.kotlin_builtins")
                pickFirsts.add("misc/registry.properties")
                pickFirsts.add("META-INF/extensions/compiler.xml")
                pickFirsts.add("META-INF/proguard/androidx-service.pro")
                pickFirsts.add("kotlin/concurrent/atomics/atomics.kotlin_builtins")
                pickFirsts.add("kotlin/annotation/annotation.kotlin_builtins")
                pickFirsts.add("kotlin/coroutines/coroutines.kotlin_builtins")
                pickFirsts.add("kotlin/internal/internal.kotlin_builtins")
                pickFirsts.add("kotlin/jvm/jvm.kotlin_builtins")
                pickFirsts.add("kotlin/math/math.kotlin_builtins")
                pickFirsts.add("kotlin/sequences/sequences.kotlin_builtins")
                pickFirsts.add("kotlin/text/text.kotlin_builtins")
                pickFirsts.add("DebugProbesKt.bin")
                pickFirsts.add("kotlinManifest.properties")
                pickFirsts.add("kotlin/kotlin.kotlin_builtins") // <<< ADD THIS LINE
                pickFirsts.add("messages/CoreBundle.properties") // <<< ADD THIS LINE
                pickFirsts.add("messages/UtilBundle.properties") // <<< ADD THIS LINE
                pickFirsts.add("messages/CoreDeprecatedMessagesBundle.properties") // <<< ADD THIS LINE
                pickFirsts.add("kotlin/ranges/ranges.kotlin_builtins") // <<< ADD THIS LINE
                pickFirsts.add("META-INF/kotlinx_coroutines_core.version") // <<< ADD THIS LINE
                pickFirsts.add("messages/JavaPsiBundle.properties") // <<< ADD THIS LINE







                // Any other proactive additions you might have made

        // ... other android configurations ...


            // Proactive additions for common Kotlin/KSP/Compiler internals:
           // pickFirsts.add("META-INF/MANIFEST.MF")
           // pickFirsts.add("META-INF/INDEX.LIST")
           // pickFirsts.add("META-INF/io.netty.versions.properties") // If you ever use Netty indirectly
           // pickFirsts.add("META-INF/LICENSE*") // Catches LICENSE, LICENSE.txt, LICENSE.md etc.
           // pickFirsts.add("META-INF/NOTICE*")  // Catches NOTICE, NOTICE.txt, NOTICE.md etc.
            //pickFirsts.add("META-INF/ASL2.0")
           // pickFirsts.add("META-INF/DEPENDENCIES")
           // pickFirsts.add("META-INF/kotlin-tooling-metadata.json")

            // More Kotlin built-ins (some might be redundant with your existing ones if paths are slightly different)
           // pickFirsts.add("kotlin/comparisons/comparisons.kotlin_builtins")
           // pickFirsts.add("kotlin/jvm/internal/jvm_internal.kotlin_builtins")
          //  pickFirsts.add("kotlin/ranges/ranges.kotlin_builtins")
            //pickFirsts.add("kotlin/time/time.kotlin_builtins")
          //  pickFirsts.add("kotlin/unsigned/unsigned.kotlin_builtins")

            // KSP related (if errors point to files specifically in META-INF/services/ for KSP)
            // pickFirsts.add("META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider")

            // Sometimes an empty directory causes issues, though less common for pickFirsts
            // pickFirsts.add("") // Use with extreme caution, usually for 'excludes' of empty dirs

            // If you see errors for specific *.kotlin_module files
            // pickFirsts.add("META-INF/*.kotlin_module") // Wildcard for all kotlin_module files

        }
    }


    // --- END OF BLOCK ---

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjvm-default=all")
        freeCompilerArgs += "-Xopt-in=androidx.media3.common.util.UnstableApi"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
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

    // Room compiler processing testing with auto-value exclusion
    implementation(libs.androidx.room.compiler.processing.testing) {
        exclude(group = "com.google.auto.value", module = "auto-value")
    }

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
    implementation(libs.firebase.dataconnect)

    // Google Play Services
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.identity)
    implementation(libs.play.services.auth.v2130)
    implementation(libs.gms.play.services.maps.v1920)
    implementation(libs.play.services.location)

    implementation(libs.glide)
    implementation(libs.picasso)
    implementation(libs.hdodenhof.circleimageview)

    // KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)

    // Third-party
    implementation(libs.facebook.login.v1600)

    // Media & Sceneform (if used)
    implementation(libs.media3.common)
    implementation(libs.androidx.scenecore) // Assuming this is the primary scenecore lib
    // implementation(libs.scenecore) // If this is different and also needed
    implementation(libs.impress)
    implementation(libs.firebase.crashlytics.buildtools)
    // libs.androidx.room.compiler.processing.testing is already above with the exclusion

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
