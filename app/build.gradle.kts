import com.android.build.api.dsl.ApplicationExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

configure<ApplicationExtension> {
    namespace = "nep.timeline.cirno"
    compileSdk = 36
    val buildTime = SimpleDateFormat("MMddHHmm", Locale.getDefault()).format(Date())

    defaultConfig {
        minSdk = 31
        targetSdk = 35
        versionCode = 9
        versionName = "${versionCode}-${buildTime}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    val freezerType = "Cirno"
    val ciKeystorePath = System.getenv("CI_KEYSTORE")
    val ciKeystorePassword = System.getenv("CI_KEYSTORE_PASSWORD")
    val ciKeyAlias = System.getenv("CI_KEY_ALIAS") ?: "cirno"

    signingConfigs {
        if (!ciKeystorePath.isNullOrBlank() && !ciKeystorePassword.isNullOrBlank()) {
            create("ciRelease") {
                storeFile = file(ciKeystorePath)
                storePassword = ciKeystorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (!ciKeystorePath.isNullOrBlank() && !ciKeystorePassword.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
            buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
            buildConfigField("String", "FREEZER_TYPE", "\"$freezerType\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
            buildConfigField("String", "FREEZER_TYPE", "\"$freezerType\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = false
        buildConfig = true
        aidl = true
    }

    sourceSets["main"].jniLibs.directories.add("src/main/jniLibs")
}

// Native Views are the shipping UI; keep legacy Compose source in git but out
// of compilation so its runtime and GPU blur dependencies are not packaged.
kotlin {
    sourceSets.named("main") {
        kotlin.exclude("**/ui/**")
    }
}

dependencies {
    implementation(project(":librekernel"))
    implementation(libs.gson)
    implementation(libs.commons.io)
    compileOnly(libs.api)
    implementation(libs.service)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.commons.lang3)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val libsuVersion = "6.0.0"
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:service:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:io:$libsuVersion")
}
