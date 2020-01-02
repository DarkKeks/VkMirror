import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("application")
    kotlin("jvm") version "1.3.61"
}

group = "ru.darkkeks"
version = "1.0-SNAPSHOT"

application {
    mainClassName = "ru.darkkeks.vkmirror.MainKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("com.vk.api:sdk:1.0.2")

    implementation("org.apache.logging.log4j", "log4j-slf4j-impl", "2.8.2")
    implementation("org.apache.logging.log4j", "log4j-api", "2.8.2")
    implementation("org.apache.logging.log4j", "log4j-core", "2.8.2")

    implementation("org.litote.kmongo:kmongo-coroutine:3.11.2")

    implementation("org.kodein.di:kodein-di-generic-jvm:6.5.0")

    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
}

repositories {
    mavenCentral()
    jcenter()
}

// TODO Doesn't work with kotlin?
tasks.withType(JavaExec::class) {
    file("local.env").readLines().filter { it.contains("=") }.forEach {
        val (key, value) = it.split("=", limit = 2)
        environment(key, value)
    }
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}