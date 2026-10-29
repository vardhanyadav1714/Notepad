import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

val releaseProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}
fun signingValue(property: String, environment: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: releaseProperties.getProperty(property)?.takeIf { it.isNotBlank() }
val releaseStorePassword = signingValue("storePassword", "DRAWLY_KEYSTORE_PASSWORD")
val releaseKeyPassword = signingValue("keyPassword", "DRAWLY_KEY_PASSWORD")
val hasReleaseSigning = releaseStorePassword != null && releaseKeyPassword != null
require((releaseStorePassword == null) == (releaseKeyPassword == null)) {
    "Supply both storePassword and keyPassword in local keystore.properties or DRAWLY signing environment variables."
}

val verifyProductionSigning by tasks.registering {
    doLast {
        check(hasReleaseSigning) { "Release signing requires the existing keystore credentials in local keystore.properties or DRAWLY signing environment variables." }
        check(providers.gradleProperty("devicePreview").orNull != "true") { "Production releases must use the Drawly application ID, without devicePreview=true." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyProductionSigning) }

// Staging/Hetzner builds can select an origin without changing the live default.
val collaborationOrigin = providers.gradleProperty("collaborationOrigin").orNull ?: "https://drawly.smartattendance.xyz"
// Explicit new-domain builds can advance independently of the legacy defaults.
val drawlyVersionCode = providers.gradleProperty("drawlyVersionCode").orNull?.toInt() ?: 31
val drawlyVersionName = providers.gradleProperty("drawlyVersionName").orNull ?: "2.4.1"
require(drawlyVersionCode > 0 && drawlyVersionName.isNotBlank()) { "Supply a positive version code and non-empty version name." }
val collaborationUri = URI(collaborationOrigin)
require(collaborationUri.scheme == "https" && collaborationUri.host != null && collaborationUri.rawUserInfo == null &&
    collaborationUri.rawQuery == null && collaborationUri.rawFragment == null && collaborationUri.path.orEmpty() in listOf("", "/")) {
    "collaborationOrigin must be an HTTPS origin, without credentials, path, query or fragment."
}

android {
    namespace = "in.innovaticshub.notepad"
    compileSdk = 36

    defaultConfig {
        val devicePreview=providers.gradleProperty("devicePreview").orNull=="true"
        applicationId = if(devicePreview) "in.innovaticshub.notepad.preview" else "in.innovaticshub.notepad"
        manifestPlaceholders["appLabel"]=if(devicePreview) "Notes Preview" else "@string/app_name"
        manifestPlaceholders["collaborationHost"] = collaborationUri.host
        buildConfigField("String", "COLLABORATION_ORIGIN", "\"${collaborationOrigin.trimEnd('/')}\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${if(collaborationUri.host == "drawly.in") "https://drawly.in/privacy" else "https://www.freeprivacypolicy.com/live/286e718d-0906-4361-a68b-3292405405f8"}\"")
        minSdk = 24
        targetSdk = 36
        versionCode = drawlyVersionCode
        versionName = drawlyVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("production") {
                storeFile = rootProject.file(signingValue("storeFile", "DRAWLY_KEYSTORE_PATH") ?: "drawly.jks")
                storePassword = releaseStorePassword
                keyAlias = signingValue("keyAlias", "DRAWLY_KEY_ALIAS") ?: "drawly"
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = true
            isShrinkResources = true
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
