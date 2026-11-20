plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The release workflow decodes the upload keystore on the runner and passes
// these environment variables to Gradle. Local builds without them still work;
// assembleRelease simply produces an unsigned APK.
val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH").orEmpty()
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD").orEmpty()
val releaseSigningReady = releaseStorePath.isNotBlank() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

android {
    namespace = "com.padanam.psc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.padanam.psc"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.4.5"
    }

    // A committed debug key keeps every CI build signed by the same
    // certificate, so new APKs install over the previous ones. Debug keys are
    // not secrets; all builds here are public pre-releases.
    signingConfigs {
        create("stableDebug") {
            storeFile = file("padanam-debug.p12")
            storeType = "PKCS12"
            storePassword = "padanam"
            keyAlias = "padanam-debug"
            keyPassword = "padanam"
        }
        if (releaseSigningReady) {
            create("upload") {
                storeFile = file(releaseStorePath)
                storeType = System.getenv("ANDROID_KEYSTORE_TYPE")
                    ?.takeIf { it.isNotBlank() } ?: "JKS"
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("upload")
            }
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
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
