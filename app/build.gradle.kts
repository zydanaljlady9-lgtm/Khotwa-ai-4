plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// قيم اختيارية تُمرَّر وقت البناء (من أسرار GitHub أو gradle.properties).
fun cfg(name: String): String =
    ((System.getenv(name) ?: (project.findProperty(name) as String?) ?: "")
        .trim().replace("\\", "").replace("\"", ""))

android {
    namespace = "com.khotwa.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khotwa.guide"
        minSdk = 30
        targetSdk = 34
        buildConfigField("String", "PROXY_URL", "\"${cfg("PROXY_URL")}\"")
        buildConfigField("String", "PROXY_TOKEN", "\"${cfg("PROXY_TOKEN")}\"")
        buildConfigField("String", "AI_KEY", "\"${cfg("AI_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${cfg("GOOGLE_WEB_CLIENT_ID")}\"")
        versionCode = 36
        versionName = "3.4.1"
        resourceConfigurations += listOf("en", "ar", "fr", "es", "pt", "tr", "in", "hi", "ur")
    }

    signingConfigs {
        create("debugConfig") {
            val rootKeystore = file("${rootDir}/debug.keystore")
            storeFile = if (rootKeystore.exists()) rootKeystore else file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // نفس توقيع نسخ debug حتى يعمل التحديث فوق النسخة المثبتة.
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // تسجيل الدخول بحساب Google
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // ذكاء داخل الهاتف (Gemini Nano عبر AICore) — يعمل فقط على الهواتف المدعومة.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
