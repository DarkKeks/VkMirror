import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("application")
    kotlin("jvm") version "1.3.61"
}

group = "ru.darkkeks"
version = "1.0-SNAPSHOT"

application {
    mainClassName = "ru.darkkeks.vkmirror.MainMain"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("com.google.inject:guice:4.2.2")

    implementation("com.vk.api:sdk:1.0.2")

    implementation("org.telegram:telegrambots:4.3.1")

    implementation("com.zaxxer:HikariCP:3.3.1")
    implementation("org.postgresql:postgresql:42.2.5")

    implementation("org.apache.logging.log4j", "log4j-slf4j-impl", "2.8.2")
    implementation("org.apache.logging.log4j", "log4j-api", "2.8.2")
    implementation("org.apache.logging.log4j", "log4j-core", "2.8.2")

    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
}

repositories {
    mavenCentral()
}

tasks.withType(JavaExec::class) {
    file("local.env").readLines().filter { it.contains("=") }.forEach {
        val (key, value) = it.split("=", limit = 2)
        environment(key, value)
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "11"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "11"
}