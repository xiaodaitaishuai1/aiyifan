import com.bytedance.android.plugin.extensions.AabResGuardExtension
import com.github.megatronking.stringfog.plugin.StringFogExtension
import com.github.megatronking.stringfog.plugin.StringFogMode
import com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.bytedance.android.aabResGuard")
    id("stringfog")
}

configure<StringFogExtension> {
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"
    enable = true
    fogPackages = arrayOf("com.aiyifan.app")
    kg = RandomKeyGenerator()
    mode = StringFogMode.bytes
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
}

configure<AabResGuardExtension> {
    mappingFile = file("mapping.txt").toPath()
    whiteList = setOf("*.R.raw.*", "*.R.drawable.icon")
    obfuscatedBundleFileName = "app_build.aab"
    mergeDuplicatedRes = true
    enableFilterFiles = true
    filterList = setOf("META-INF/*", "BUNDLE-METADATA/*")
    enableFilterStrings = false
    unusedStringPath = file("unused.txt").toPath().toString()
    languageWhiteList = setOf("en", "zh")
}

val appConfigFile = rootProject.file("app-config.gradle")
apply(from = appConfigFile.takeIf { it.isFile } ?: rootProject.file("app-config.gradle.example"))

val APPLICATION_ID: String by project.extra
val VERSION_NAME: String by project.extra
val VERSION_CODE: String by project.extra
val STORE_PATH: String by project.extra
val STORE_PASSWORD: String by project.extra
val KEY_ALIAS: String by project.extra
val KEY_PASSWORD: String by project.extra

val hasReleaseSigning = listOf(STORE_PATH, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).all { it.isNotBlank() }

android {
    namespace = "com.aiyifan.app"
    compileSdk = 36

    defaultConfig {
        applicationId = APPLICATION_ID
        minSdk = 24
        targetSdk = 36
        versionCode = VERSION_CODE.toInt()
        versionName = VERSION_NAME

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                keyAlias = KEY_ALIAS
                keyPassword = KEY_PASSWORD
                storeFile = file(STORE_PATH)
                storePassword = STORE_PASSWORD
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.configureEach {
    if (!hasReleaseSigning && name in setOf(
            "packageRelease",
            "bundleRelease",
            "validateSigningRelease",
        )
    ) {
        doFirst {
            throw GradleException(
                "Release signing requires STORE_PATH, STORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD in app-config.gradle",
            )
        }
    }
}

dependencies {
    implementation("com.github.mobcoding.StringFog:xor:5.2.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
}
