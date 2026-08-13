plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

fun signingValue(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name.uppercase().replace('.', '_'))

val releaseSigningPropertyNames = listOf(
    "release.keystore.path",
    "release.key.alias",
    "release.store.password",
    "release.key.password",
)
val releaseSigningValues = releaseSigningPropertyNames.associateWith(::signingValue)
val hasReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.aiyifan.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiyifan.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                keyAlias = releaseSigningValues.getValue("release.key.alias")
                keyPassword = releaseSigningValues.getValue("release.key.password")
                storeFile = file(releaseSigningValues.getValue("release.keystore.path"))
                storePassword = releaseSigningValues.getValue("release.store.password")
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
                "Release signing requires: ${releaseSigningPropertyNames.joinToString()}",
            )
        }
    }
}

val libboxAar = layout.projectDirectory.file("libs/libbox.aar")

val buildLibboxAar by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Builds the local sing-box Android binding when it is missing."
    inputs.dir(rootProject.layout.projectDirectory.dir("third_party/sing-box"))
    inputs.file(rootProject.layout.projectDirectory.file("tools/build-libbox.ps1"))
    outputs.file(libboxAar)
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        rootProject.layout.projectDirectory.file("tools/build-libbox.ps1").asFile.absolutePath,
    )
}

tasks.named("preBuild") {
    dependsOn(buildLibboxAar)
}

dependencies {
    implementation(files("libs/libbox.aar"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.json:json:20240303")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
}
