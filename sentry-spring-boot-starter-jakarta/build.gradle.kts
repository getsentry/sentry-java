import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
    id(Config.BuildPlugins.springBoot) version Config.springBoot3Version apply false
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
    api(projects.sentrySpringJakarta)
    compileOnly(projects.sentryLogback)
    compileOnly(projects.sentryApacheHttpClient5)
    implementation(Config.Libs.springBoot3Starter)
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springWebflux)
    compileOnly(Config.Libs.servletApiJakarta)
    compileOnly(Config.Libs.springBoot3StarterAop)
    compileOnly(Config.Libs.springBoot3StarterSecurity)
    compileOnly(Config.Libs.reactorCore)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryCore)

    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
    annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentryLogback)
    testImplementation(projects.sentryApacheHttpClient5)
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockWebserver)

    testImplementation(Config.Libs.okhttp)
    testImplementation(Config.Libs.springBoot3StarterTest)
    testImplementation(Config.Libs.springBoot3StarterWeb)
    testImplementation(Config.Libs.springBoot3StarterWebflux)
    testImplementation(Config.Libs.springBoot3StarterSecurity)
    testImplementation(Config.Libs.springBoot3StarterAop)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryCore)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

jacoco {
    toolVersion = Config.QualityPlugins.Jacoco.version
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}

tasks {
    jacocoTestCoverageVerification {
        violationRules {
            rule { limit { minimum = Config.QualityPlugins.Jacoco.minimumCoverage } }
        }
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
        dependsOn(jacocoTestReport)
    }
}

task("jakartaTransformation", JavaExec::class) {
    mainClass.set("org.eclipse.transformer.cli.JakartaTransformerCLI")
    classpath = configurations.getByName("jakartaTransform") // sourceSets["main"].compileClasspath
    args = listOf("../sentry-spring-boot-starter/src/main/java/io/sentry/spring/boot", "src/main/java/io/sentry/spring/boot/jakarta", "-o", "-tf", "sentry-jakarta-text-master.properties")
}.dependsOn("jakartaTestTransformation")

task("jakartaTestTransformation", JavaExec::class) {
    mainClass.set("org.eclipse.transformer.cli.JakartaTransformerCLI")
    classpath = configurations.getByName("jakartaTransform") // sourceSets["main"].compileClasspath
    args = listOf("../sentry-spring-boot-starter/src/test/kotlin/io/sentry/spring/boot", "src/test/kotlin/io/sentry/spring/boot/jakarta", "-o", "-tf", "sentry-jakarta-text-master.properties")
}.dependsOn("jakartaMainClassTransformation")

task("jakartaMainClassTransformation", JavaExec::class) {
    mainClass.set("org.eclipse.transformer.cli.JakartaTransformerCLI")
    classpath = configurations.getByName("jakartaTransform") // sourceSets["main"].compileClasspath
    args = listOf("../sentry-spring-boot-starter/src/test/kotlin/com/acme", "src/test/kotlin/com/acme", "-o", "-tf", "sentry-jakarta-text-master.properties")
}

// tasks.named("build").dependsOn("jakartaTransformation")

buildConfig {
    useJavaOutput()
    packageName("io.sentry.spring.boot.jakarta")
    buildConfigField("String", "SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME", "\"${Config.Sentry.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME}\"")
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
