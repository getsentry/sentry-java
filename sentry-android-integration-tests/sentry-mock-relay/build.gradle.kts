
plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version Config.TestLibs.ktorVersion
    kotlin("plugin.serialization") version Config.TestLibs.pluginSerializationVersion
}

group = "io.sentry.mock-relay"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(Config.TestLibs.kotlinxSerializationJson)
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation(Config.TestLibs.logbackClassic)
    implementation("io.ktor:ktor-server-config-yaml")
}
