plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ankidroid.companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ankidroid.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Copy a versioned APK alongside the original output after assembleDebug.
val versionNameForApk = android.defaultConfig.versionName ?: "unknown"
val copyDebugApkWithVersion = tasks.register<Copy>("copyDebugApkWithVersion") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    // Use a separate folder to avoid clashing with Android's own outputs.
    into(layout.buildDirectory.dir("outputs/apk/versioned"))
    rename { "AnkiDroid-Companion-$versionNameForApk.apk" }
}
afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy(copyDebugApkWithVersion)
}

dependencies {

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.github.ankidroid:Anki-Android:v2.17alpha8")
    implementation("androidx.work:work-runtime:2.7.0")

}
