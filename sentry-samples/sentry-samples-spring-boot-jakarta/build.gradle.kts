import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id(Config.BuildPlugins.springBoot) version Config.springBoot3Version
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version Config.kotlinVersion
    id("com.apollographql.apollo3") version "3.8.2"
}

group = "io.sentry.sample.spring-boot-jakarta"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    implementation(Config.Libs.springBoot3StarterSecurity)
    implementation(Config.Libs.springBoot3StarterActuator)
    implementation(Config.Libs.springBoot3StarterWeb)
    implementation(Config.Libs.springBoot3StarterWebsocket)
    implementation(Config.Libs.springBoot3StarterGraphql)
    implementation(Config.Libs.springBoot3StarterQuartz)
    implementation(Config.Libs.springBoot3StarterWebflux)
    implementation(Config.Libs.springBoot3StarterAop)
    implementation(Config.Libs.aspectj)
    implementation(Config.Libs.springBoot3Starter)
    implementation(Config.Libs.kotlinReflect)
    implementation(Config.Libs.springBootStarterJdbc)
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(projects.sentrySpringBootStarterJakarta)
    implementation(projects.sentryLogback)
    implementation(projects.sentryGraphql)
    implementation(projects.sentryQuartz)

    // database query tracing
    implementation(projects.sentryJdbc)
    runtimeOnly(Config.TestLibs.hsqldb)
    testImplementation(Config.Libs.springBoot3StarterTest) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation("ch.qos.logback:logback-classic:1.3.5")
    testImplementation(Config.Libs.slf4jApi2)
    testImplementation(Config.Libs.apolloKotlin)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

tasks.register<Test>("systemTest").configure {
    group = "verification"
    description = "Runs the System tests"

//    maxParallelForks = Runtime.getRuntime().availableProcessors() / 2

    // Cap JVM args per test
    minHeapSize = "128m"
    maxHeapSize = "1g"

    filter {
        includeTestsMatching("io.sentry.systemtest*")
    }
}

tasks.named("test").configure {
    require(this is Test)

    filter {
        excludeTestsMatching("io.sentry.systemtest.*")
    }
}

apollo {
    service("service") {
        srcDir("src/test/graphql")
        packageName.set("io.sentry.samples.graphql")
        outputDirConnection {
            connectToKotlinSourceSet("test")
        }
    }
}
