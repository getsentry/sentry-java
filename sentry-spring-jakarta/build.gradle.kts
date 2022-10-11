import com.android.build.gradle.internal.tasks.factory.dependsOn
import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    `java-library`
    kotlin("jvm")
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
    id(Config.BuildPlugins.springBoot) version Config.springBoot3Version apply false
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

buildscript {
    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${Config.springBoot3Version}")
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
}

val jakartaTransform by configurations.creating

dependencies {

    jakartaTransform("org.eclipse.transformer:org.eclipse.transformer:0.5.0")
    jakartaTransform("org.eclipse.transformer:org.eclipse.transformer.cli:0.5.0")
    jakartaTransform("org.eclipse.transformer:org.eclipse.transformer.jakarta:0.5.0")

    api(projects.sentry)
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springAop)
    compileOnly(Config.Libs.springSecurityWeb)
    compileOnly(Config.Libs.aspectj)
    compileOnly(Config.Libs.servletApiJakarta)
    compileOnly(Config.Libs.slf4jApi)

    compileOnly(Config.Libs.springWebflux)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.Libs.springBootStarterTest)
    testImplementation(Config.Libs.springBootStarterWeb)
    testImplementation(Config.Libs.springBootStarterWebflux)
    testImplementation(Config.Libs.springBootStarterSecurity)
    testImplementation(Config.Libs.springBootStarterAop)
    testImplementation(Config.TestLibs.awaitility)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-deprecation")
}

task("jakartaTransformation", JavaExec::class) {
    main = "org.eclipse.transformer.cli.JakartaTransformerCLI"
    classpath = configurations.getByName("jakartaTransform") //sourceSets["main"].compileClasspath
    args = listOf("../sentry-spring/src", "src", "-o", "-tf", "sentry-jakarta-text-master.properties")
}

tasks.named("build").dependsOn("jakartaTransformation")

buildConfig {
    useJavaOutput()
    packageName("io.sentry.spring")
    buildConfigField("String", "SENTRY_SPRING_SDK_NAME", "\"${Config.Sentry.SENTRY_SPRING_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

val generateBuildConfig by tasks
tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateBuildConfig)
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
}
