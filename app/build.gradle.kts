import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

/**
 * Local secrets, read from the gitignored `secrets.properties` (see
 * secrets.properties.template).
 *
 * A missing file or a blank key is deliberately not an error. The Maps key is
 * only needed for one screen, and a clean clone - or CI - must still be able to
 * build and run everything else. The map degrades to a message instead.
 */
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val mapsApiKey: String = secrets.getProperty("MAPS_API_KEY").orEmpty().trim()

/**
 * Release signing, read from the gitignored `keystore.properties`.
 *
 * Absent on every machine but the one that publishes, and that is deliberate:
 * a clean clone still builds a release APK, it just comes out unsigned. The
 * alternative - committing the keystore or its password - would put the ability
 * to publish an update to a medical records app in the repository.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasSigningConfig = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.ss.medrecord"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ss.medrecord"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Maps SDK reads its key from a manifest meta-data tag; the Places
        // SDK is initialised in code and needs it as a value. Neither is a
        // secret once the APK ships - Google's model for Android Maps keys is
        // Cloud-console restriction by package and signing certificate, not
        // secrecy - but keeping it out of the repo stops a public clone from
        // spending someone else's quota.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        // Lets the UI say "maps are not configured" rather than showing a grey
        // rectangle and leaving the user to guess.
        buildConfigField("Boolean", "HAS_MAPS_KEY", mapsApiKey.isNotBlank().toString())
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v2+ only. v1 (jar signing) is unnecessary above minSdk 24 and
                // its per-entry signatures are the weaker scheme.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimise and obfuscate. Obfuscation is not a security
            // control on its own - the data protections are the database and
            // file encryption - but shipping a medical app with every class and
            // method name intact hands an attacker a free map.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                // Unsigned rather than debug-signed. A release APK carrying the
                // debug certificate is one that can be installed over a real
                // build, and it should be obvious that it is not shippable.
                null
            }
        }

        debug {
            // Only the version name is suffixed. An applicationId suffix would
            // let a debug build sit alongside a store install, but google-services.json
            // registers exactly com.ss.medrecord - a suffixed id fails Firebase
            // initialisation at launch, which is a worse trade than sharing the id.
            versionNameSuffix = "-debug"
        }
    }

    lint {
        // Lint runs as part of the release path and blocks on real errors.
        abortOnError = true
        warningsAsErrors = false
        // Dependency-freshness noise: several versions here are pinned on
        // purpose (maps-compose to the last Kotlin 2.2 build, AGP to the one
        // KSP matches), so "newer available" is not actionable.
        disable += listOf(
            "NewerVersionAvailable",
            "GradleDependency",
            "AndroidGradlePluginVersion",
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Makes java.time available down to minSdk 24. This app is built around
        // dates - dates of birth, visit dates, reminder schedules - and the
        // legacy Calendar API is a reliable source of timezone bugs.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // android.util.Log throws by default under the JVM stub. It is a
            // side-effect-only API, so returning defaults is the honest
            // behaviour and keeps logging statements out of test setup.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room exports its schema here so migrations can be diffed in review (Phase 1+).
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ProcessLifecycleOwner: drives the sync-on-foreground trigger (spec 6.2).
    implementation(libs.androidx.lifecycle.process)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Optional app lock (see core/biometric). Brings androidx.fragment with it,
    // which is why MainActivity is a FragmentActivity: BiometricPrompt shows
    // itself through the fragment manager and cannot attach to a bare
    // ComponentActivity.
    implementation(libs.androidx.biometric)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    // Registers this device for server-initiated work (Phase 6). Reminders
    // themselves are local alarms and do not depend on it.
    implementation(libs.firebase.messaging)

    // Files: EXIF orientation, so a photographed report is not shown sideways.
    // PDF rendering and image decoding both use platform APIs, so no image
    // loading or PDF library is pulled in for them.
    implementation(libs.androidx.exifinterface)

    // Maps, location and place search (Phase 8). maps-compose wraps the Maps
    // SDK in a real composable rather than an AndroidView holding a MapView
    // whose lifecycle has to be driven by hand.
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.places)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)

    // Async / serialization
    implementation(libs.kotlinx.coroutines.android)
    // Bridges Firebase Task<T> to suspend functions via Task.await().
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
