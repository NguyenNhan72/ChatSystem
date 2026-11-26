import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Wire protocol shared by :server and :app — one definition of every REST body and WebSocket event.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// JVM 11 bytecode so the Android app (Java 11 target) can consume it.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
