import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id(Config.BuildPlugins.springBoot) version Config.springBootVersion
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version Config.kotlinVersion
    id("com.apollographql.apollo3") version "3.8.2"
}

group = "io.sentry.sample.spring-boot"
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
    implementation(Config.Libs.springBootStarterSecurity)
    implementation(Config.Libs.springBootStarterWeb)
    implementation(Config.Libs.springBootStarterWebsocket)
    implementation(Config.Libs.springBootStarterWebflux)
    implementation(Config.Libs.springBootStarterGraphql)
    implementation(Config.Libs.springBootStarterQuartz)
    implementation(Config.Libs.springBootStarterAop)
    implementation(Config.Libs.springBootStarterActuator)
    implementation(Config.Libs.aspectj)
    implementation(Config.Libs.springBootStarter)
    implementation(Config.Libs.kotlinReflect)
    implementation(Config.Libs.springBootStarterJdbc)
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(projects.sentrySpringBootStarter)
    implementation(projects.sentryLogback)
    implementation(projects.sentryGraphql)
    implementation(projects.sentryQuartz)

    // database query tracing
    implementation(projects.sentryJdbc)
    runtimeOnly(Config.TestLibs.hsqldb)
    testImplementation(Config.Libs.springBootStarterTest) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.Libs.logbackClassic)
    testImplementation(Config.Libs.slf4jApi2)
    testImplementation(Config.Libs.apolloKotlin)
    testImplementation("org.apache.httpcomponents:httpclient")
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
