import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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

    // Gemini API key from local.properties
    val localProperties = Properties()

    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") as? String 
        ?: project.findProperty("GEMINI_API_KEY") as? String 
        ?: System.getenv("GEMINI_API_KEY") 
        ?: ""

    // Load version code and version name from version.properties
    val versionPropsFile = project.file("version.properties")
    val versionProps = Properties()
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { versionProps.load(it) }
    } else {
        versionProps["VERSION_CODE"] = "3"
        versionProps["VERSION_NAME"] = "3.0.0"
    }

    val currentVersionCode = versionProps.getProperty("VERSION_CODE").toInt()
    val currentVersionName = versionProps.getProperty("VERSION_NAME")

    defaultConfig {
        applicationId = "com.presyohan.app"
        minSdk = 24
        targetSdk = 35
        versionCode = currentVersionCode
        versionName = currentVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject Supabase URL and anon key into BuildConfig
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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
    // No longer needed in AGP 9.0+ as it defaults to targetCompatibility
    // compilerOptions {
    //     jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    // }
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
    // Ensure Log4j desktop API is excluded globally for Android compatibility
    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
}

dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
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
    implementation(libs.androidx.swiperefreshlayout)
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

    // Excel generation using FastExcel (no XMLBeans/POI)
    implementation("org.dhatim:fastexcel:0.19.0")
    implementation("org.dhatim:fastexcel-reader:0.19.0")

    // StAX API and Aalto XML implementation for Android Excel reading compatibility
    implementation("javax.xml.stream:stax-api:1.0-2")
    implementation("com.fasterxml:aalto-xml:1.3.4")

    implementation("io.coil-kt:coil:2.6.0")

    // Facebook Shimmer for loading skeletons
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // SwipeRefreshLayout for pull-to-refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // QR Code generation & scanning
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

// Tasks for version incrementation
tasks.register("incrementPatch") {
    group = "versioning"
    description = "Bumps the version code (+1) and patch version (e.g., 3.0.0 -> 3.0.1)."
    doLast {
        val propertiesFile = file("version.properties")
        val properties = Properties()
        propertiesFile.inputStream().use { properties.load(it) }
        
        val code = properties.getProperty("VERSION_CODE").toInt()
        val name = properties.getProperty("VERSION_NAME")
        
        val nextCode = code + 1
        val parts = name.split(".")
        if (parts.size >= 3) {
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            val patch = parts[2].toInt()
            val nextName = "$major.$minor.${patch + 1}"
            
            properties.setProperty("VERSION_CODE", nextCode.toString())
            properties.setProperty("VERSION_NAME", nextName)
            propertiesFile.outputStream().use { properties.store(it, "Auto-incremented patch version") }
            println("Version bumped to: $nextName (Code: $nextCode)")
        } else {
            throw IllegalArgumentException("Invalid version name format: $name. Expected major.minor.patch")
        }
    }
}

tasks.register("incrementMinor") {
    group = "versioning"
    description = "Bumps the version code (+1) and minor version, resetting patch to 0 (e.g., 3.0.1 -> 3.1.0)."
    doLast {
        val propertiesFile = file("version.properties")
        val properties = Properties()
        propertiesFile.inputStream().use { properties.load(it) }
        
        val code = properties.getProperty("VERSION_CODE").toInt()
        val name = properties.getProperty("VERSION_NAME")
        
        val nextCode = code + 1
        val parts = name.split(".")
        if (parts.size >= 2) {
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            val nextName = "$major.${minor + 1}.0"
            
            properties.setProperty("VERSION_CODE", nextCode.toString())
            properties.setProperty("VERSION_NAME", nextName)
            propertiesFile.outputStream().use { properties.store(it, "Auto-incremented minor version") }
            println("Version bumped to: $nextName (Code: $nextCode)")
        } else {
            throw IllegalArgumentException("Invalid version name format: $name. Expected major.minor[.patch]")
        }
    }
}

tasks.register("incrementMajor") {
    group = "versioning"
    description = "Bumps the version code (+1) and major version, resetting minor and patch to 0 (e.g., 3.0.1 -> 4.0.0)."
    doLast {
        val propertiesFile = file("version.properties")
        val properties = Properties()
        propertiesFile.inputStream().use { properties.load(it) }
        
        val code = properties.getProperty("VERSION_CODE").toInt()
        val name = properties.getProperty("VERSION_NAME")
        
        val nextCode = code + 1
        val parts = name.split(".")
        if (parts.isNotEmpty()) {
            val major = parts[0].toInt()
            val nextName = "${major + 1}.0.0"
            
            properties.setProperty("VERSION_CODE", nextCode.toString())
            properties.setProperty("VERSION_NAME", nextName)
            propertiesFile.outputStream().use { properties.store(it, "Auto-incremented major version") }
            println("Version bumped to: $nextName (Code: $nextCode)")
        } else {
            throw IllegalArgumentException("Invalid version name format: $name. Expected major.minor[.patch]")
        }
    }
}
