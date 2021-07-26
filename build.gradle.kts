group = "me.appr.booking"

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.jmailen.gradle:kotlinter-gradle:3.4.5")
    }
}

plugins {
    kotlin("jvm") version "1.5.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.21" apply false
}
