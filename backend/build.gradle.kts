plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "com.globalchat"
version = "1.0.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.globalchat.ApplicationKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.6.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.1")
    implementation("io.ktor:ktor-server-websockets-jvm:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")
}