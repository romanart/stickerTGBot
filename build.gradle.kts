import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.11"
    application
    id("org.hidetake.ssh") version "2.9.0"
}

apply {
    plugin("kotlinx-serialization")
}

group = "RomArt"
version = "1.0-BETA"

apply(from = "deploy.gradle")

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven(url = "https://dl.bintray.com/orangy/maven")
}

buildscript {
    repositories { jcenter() }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.11")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.3.11")
    }
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.telegram:telegrambots:4.1")
    compile("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.9.1")
    compile("org.slf4j:slf4j-log4j12:1.7.25")
    compile("io.github.microutils:kotlin-logging:1.6.22")
    compile("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.1.0-dev-4")
}

application {
    mainClassName = "MainKt"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
