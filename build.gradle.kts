buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
    dependencies {
        classpath("com.github.mobcoding.StringFog:gradle-plugin:5.2.2")
        classpath("com.github.mobcoding.StringFog:xor:5.2.2")
    }
}

plugins {
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
}
