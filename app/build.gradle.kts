import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.roziqrizal.habitflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.roziqrizal.habitflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Client ID Google (tipe Web, dibuat di tahap 25) dipakai sebagai serverClientId saat
        // minta ID token lewat Credential Manager - harus SAMA dengan GOOGLE_ANDROID_CLIENT_ID
        // di .env.production server, supaya aud token cocok saat diverifikasi (lihat
        // GoogleAuthController di server/). Diisi lewat local.properties, tidak di-commit.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField(
            "String", "GOOGLE_SERVER_CLIENT_ID",
            "\"${localProps.getProperty("GOOGLE_SERVER_CLIENT_ID", "")}\"",
        )
    }

    // Keystore release disimpan di luar repo. Lokasinya bisa diganti lewat properti
    // Gradle `habitflow.signing`. Kalau file tidak ada, release dibuat tanpa signing.
    val signingFile = file(
        providers.gradleProperty("habitflow.signing").orNull
            ?: "${System.getProperty("user.home")}/.habitflow/keystore.properties"
    )
    val releaseSigning = if (signingFile.exists()) {
        val props = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = releaseSigning
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha08")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Login Google lewat Credential Manager (tahap 25/26/28 - prasyarat akun di Android).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    // org.json bawaan Android tidak ada di JVM unit test; versi ini setara dan hanya dipakai untuk tes.
    testImplementation("org.json:json:20240303")
}
