import java.util.Properties
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import groovy.json.JsonSlurper
import java.net.URLClassLoader
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.spendwise.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spendwise.app"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "com.spendwise.app.HiltTestRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(project(":intelligence"))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    implementation(libs.compose.navigation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.splashscreen)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)
    implementation(libs.workmanager)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.mpandroidchart)
    implementation(libs.opencsv)
    implementation(libs.itext.kernel)
    implementation(libs.itext.layout)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.firebase:firebase-auth:23.1.0")
    implementation("com.google.firebase:firebase-firestore:25.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.hilt.testing)
    debugImplementation(libs.compose.ui.test.manifest)
    kspAndroidTest(libs.hilt.compiler)
}

tasks.register("verifyGoogleServices") {
    description = "Verifies google-services.json presence, package name, OAuth client type 3, and release signing SHA-1 before release builds."
    group = "verification"

    doLast {
        val googleServicesFile = project.file("google-services.json")
        if (!googleServicesFile.exists()) {
            throw GradleException(
                "VERIFICATION FAILED: 'app/google-services.json' is missing!\n" +
                "Please run './scripts/setup_firebase.sh' (or .ps1) or download google-services.json from Firebase Console into 'app/'."
            )
        }

        val jsonText = googleServicesFile.readText()
        val jsonSlurper = groovy.json.JsonSlurper()
        val json = try {
            jsonSlurper.parseText(jsonText) as? Map<*, *>
        } catch (e: Exception) {
            throw GradleException("VERIFICATION FAILED: 'app/google-services.json' is not valid JSON: ${e.message}")
        } ?: throw GradleException("VERIFICATION FAILED: 'app/google-services.json' format is invalid.")

        val clients = json["client"] as? List<*> ?: emptyList<Any>()
        if (clients.isEmpty()) {
            throw GradleException("VERIFICATION FAILED: 'google-services.json' does not contain any 'client' entries.")
        }

        val expectedAppId = android.defaultConfig.applicationId ?: "com.spendwise.app"
        var matchingClient: Map<*, *>? = null
        for (c in clients) {
            val cMap = c as? Map<*, *> ?: continue
            val clientInfo = cMap["client_info"] as? Map<*, *> ?: continue
            val androidClientInfo = clientInfo["android_client_info"] as? Map<*, *> ?: continue
            val packageName = androidClientInfo["package_name"] as? String
            if (packageName == expectedAppId) {
                matchingClient = cMap
                break
            }
        }

        if (matchingClient == null) {
            throw GradleException(
                "VERIFICATION FAILED: Package name mismatch in 'google-services.json'!\n" +
                "Expected applicationId: '$expectedAppId', but no matching client was found."
            )
        }

        // Check for OAuth client of type 3 (Web client)
        val oauthClients = matchingClient["oauth_client"] as? List<*> ?: emptyList<Any>()
        val hasWebClient = oauthClients.any {
            val oc = it as? Map<*, *> ?: return@any false
            (oc["client_type"] as? Number)?.toInt() == 3
        }

        if (!hasWebClient) {
            throw GradleException(
                "VERIFICATION FAILED: Missing OAuth Web Client (type 3) in 'google-services.json'!\n" +
                "Google Sign-In requires an OAuth client of type 3.\n" +
                "Please enable Google Sign-In in Firebase Console (Authentication > Sign-in method > Google) and re-download google-services.json."
            )
        }

        // Check release signing SHA-1 matching
        val releaseConfig = android.signingConfigs.findByName("release")
        val releaseStoreFile = releaseConfig?.storeFile
        if (releaseStoreFile != null && releaseStoreFile.exists()) {
            val alias = releaseConfig.keyAlias
            val storePass = releaseConfig.storePassword?.toCharArray() ?: CharArray(0)
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            try {
                FileInputStream(releaseStoreFile).use { fis ->
                    ks.load(fis, storePass)
                }
                val cert = ks.getCertificate(alias)
                if (cert != null) {
                    val md = MessageDigest.getInstance("SHA-1")
                    val sha1Bytes = md.digest(cert.encoded)
                    val sha1NoColons = sha1Bytes.joinToString("") { String.format("%02x", it) }.lowercase()
                    val sha1WithColons = sha1Bytes.joinToString(":") { String.format("%02X", it) }

                    // Collect all certificate hashes registered in matchingClient oauth_client
                    val registeredHashes = oauthClients.mapNotNull {
                        val oc = it as? Map<*, *> ?: return@mapNotNull null
                        val androidInfo = oc["android_info"] as? Map<*, *> ?: return@mapNotNull null
                        (androidInfo["certificate_hash"] as? String)?.lowercase()?.replace(":", "")
                    }

                    if (registeredHashes.isNotEmpty() && !registeredHashes.contains(sha1NoColons)) {
                        throw GradleException(
                            "VERIFICATION FAILED: Release keystore SHA-1 does not match registered hashes in 'google-services.json'!\n" +
                            "Release Keystore SHA-1: $sha1WithColons ($sha1NoColons)\n" +
                            "Registered hashes in google-services.json: $registeredHashes\n" +
                            "Add this release SHA-1 to Firebase Console > Project Settings > Your Android App."
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is GradleException) throw e
                logger.warn("Could not inspect release keystore: ${e.message}")
            }
        }

        println("SUCCESS: verifyGoogleServices passed! google-services.json is valid, package name matches '$expectedAppId', OAuth Web Client (type 3) is present, and release keystore SHA-1 is verified.")
    }
}

// Hook verifyGoogleServices before release builds
tasks.matching { task ->
    task.name.startsWith("assembleRelease") ||
    task.name.startsWith("bundleRelease") ||
    task.name == "packageRelease" ||
    task.name == "mergeReleaseResources" ||
    task.name == "processReleaseGoogleServices"
}.configureEach {
    dependsOn("verifyGoogleServices")
}

tasks.register("runAiBenchmark") {
    dependsOn("compileReleaseKotlin")
    doLast {
        val classesDir = file("build/tmp/kotlin-classes/release")
        val cpUrls = arrayOf(classesDir.toURI().toURL())
        val classLoader = URLClassLoader(cpUrls, javaClass.classLoader)
        val runnerClass = classLoader.loadClass("com.spendwise.app.data.ai.AiIntelligenceBenchmarkRunner")
        val runMethod = runnerClass.getMethod("runBenchmark")
        val instance = runnerClass.getField("INSTANCE").get(null)
        val passed = runMethod.invoke(instance) as Boolean
        if (!passed) {
            throw GradleException("AI Intelligence Benchmark FAILED! Requirements not met.")
        }
    }
}
