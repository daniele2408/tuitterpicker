import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
    application
}

group = "me.daniele"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

val exposedVersion: String by project
dependencies {
    implementation("khttp:khttp:0.1.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.30.1")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    implementation("ch.qos.logback:logback-classic:1.2.6")
    implementation("com.sksamuel.hoplite:hoplite-core:2.5.2")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.5.2")
    testImplementation(kotlin("test"))
}


tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClassName = "MainKt"
}