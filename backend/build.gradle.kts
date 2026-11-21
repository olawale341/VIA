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
    implementation("io.ktor:ktor-client-core-jvm:3.6.1")
    implementation("io.ktor:ktor-client-cio-jvm:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // PostgreSQL + Connection Pool
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("com.zaxxer:HikariCP:7.0.2")
}