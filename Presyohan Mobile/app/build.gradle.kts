plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.presyohan.app"
    compileSdk = 35

    // Supabase keys from gradle.properties
    val supabaseUrl = project.findProperty("SUPABASE_URL") as? String ?: ""
    val supabaseAnonKey = project.findProperty("SUPABASE_ANON_KEY") as? String ?: ""

    defaultConfig {
        applicationId = "com.presyohan.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject Supabase URL and anon key into BuildConfig
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
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

// Force dependency to avoid compileSdk 36 requirement from androidx.browser 1.9.0
configurations.all {
    resolutionStrategy {
        force("androidx.browser:browser:1.8.0")
    }
    exclude(group = "io.github.jan-tennert.supabase", module = "postgrest-kt-android-debug")
    exclude(group = "io.github.jan-tennert.supabase", module = "auth-kt-android-debug")
    exclude(group = "io.github.jan-tennert.supabase", module = "storage-kt-android-debug")
    exclude(group = "io.github.jan-tennert.supabase", module = "realtime-kt-android-debug")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Import the Firebase BoM (kept during migration)
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    // Firebase libraries (will be removed after full cutover)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:20.7.0") // For Google Sign-In
    // Force compatible browser version for compileSdk 35
    implementation("androidx.browser:browser:1.8.0")

    // Supabase Kotlin client (Android variants pinned to 3.2.5)
    implementation("io.github.jan-tennert.supabase:postgrest-kt-android:3.2.5") {
        exclude(group = "io.github.jan-tennert.supabase", module = "postgrest-kt-android-debug")
    }
    implementation("io.github.jan-tennert.supabase:auth-kt-android:3.2.5") {
        exclude(group = "io.github.jan-tennert.supabase", module = "auth-kt-android-debug")
    }
    implementation("io.github.jan-tennert.supabase:storage-kt-android:3.2.5") {
        exclude(group = "io.github.jan-tennert.supabase", module = "storage-kt-android-debug")
    }
    implementation("io.github.jan-tennert.supabase:realtime-kt-android:3.2.5") {
        exclude(group = "io.github.jan-tennert.supabase", module = "realtime-kt-android-debug")
    }

    // Ktor HTTP client for Android (match supabase-kt 3.2.x recommended)
    implementation("io.ktor:ktor-client-okhttp:3.3.1")

    // Kotlinx Serialization JSON for Supabase Kotlin decoding
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}