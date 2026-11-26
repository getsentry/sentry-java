import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  id("io.sentry.animalsniffer.android")
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
  id("io.sentry.publish")
  id("io.sentry.api-validation")
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
}

// Build-time only: JFlex generates the SQL lexer; the generated code has no runtime dependency.
val jflex: Configuration = configurations.create("jflex")

dependencies {
  jflex(libs.jflex)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  // tests
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.google.truth)
  testImplementation(libs.javafaker)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.core)
  testImplementation(libs.msgpack)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.okhttp.tls)
  testImplementation(libs.okio)
  testImplementation(projects.sentryTestSupport)
}

sentryAnimalSniffer {
  // We manually check on Android if it's available (API 26+).
  ignoreClasses("java.time.Instant")
  // Uses java.util.function.Supplier, but must be manually invoked.
  mainExcludes("**/io/sentry/SentryWrapper.class")
}

tasks {
  test {
    // java.lang open is needed by tests that reflectively rewrite Class names; it was previously
    // provided implicitly by the jacoco test agent, which has been removed.
    jvmArgs(
      "--add-opens",
      "java.base/java.util.concurrent=ALL-UNNAMED",
      "--add-opens",
      "java.base/java.lang=ALL-UNNAMED",
    )
    // SentryEnvelopeItemTest."fromAttachment with file SecurityManager denies read access" installs
    // a SecurityManager to simulate unreadable attachments; JDK 18+ refuses that without opt-in.
    systemProperty("java.security.manager", "allow")
    environment["SENTRY_TEST_PROPERTY"] = "\"some-value\""
    environment["SENTRY_TEST_MAP_KEY1"] = "\"value1\""
    environment["SENTRY_TEST_MAP_KEY2"] = "value2"
  }
}

// Generates the SQL lexer from OpenTelemetry's grammar, vendored by scripts/sync-otel-sql.sh.
val jflexSourceFile = layout.projectDirectory.file("src/main/jflex/SqlSanitizer.jflex")
val jflexOutputDir = layout.buildDirectory.dir("generated/sources/jflex")

val generateJflex =
  tasks.register<JavaExec>("generateJflex") {
    description = "Generates the SQL lexer from src/main/jflex/SqlSanitizer.jflex."
    classpath(jflex)
    mainClass.set("jflex.Main")
    inputs.file(jflexSourceFile)
    outputs.dir(jflexOutputDir)
    // Locals, not the script's vals: lambdas that reference script vals capture the script object,
    // which the configuration cache cannot serialize.
    val sourceFile = jflexSourceFile
    val outputDir = jflexOutputDir
    argumentProviders.add(
      CommandLineArgumentProvider {
        listOf(
          "--quiet",
          "--nobak",
          "-d",
          outputDir.get().dir("io/sentry/vendor/otel/sql").asFile.absolutePath,
          sourceFile.asFile.absolutePath,
        )
      },
    )
    doFirst { outputDir.get().asFile.deleteRecursively() }
  }

sourceSets { main { java.srcDir(generateJflex) } }

// The vendored OpenTelemetry files are internal and keep upstream's Javadoc, which links classes
// that are not vendored; the generated lexer has none.
tasks.javadoc {
  exclude(
    "io/sentry/vendor/otel/sql/SqlQuery.java",
    "io/sentry/vendor/otel/sql/SqlDialect.java",
    "io/sentry/vendor/otel/sql/StringUtils.java",
    "io/sentry/vendor/otel/sql/AutoSqlSanitizer.java",
  )
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry")
  buildConfigField("String", "SENTRY_JAVA_SDK_NAME", "\"${Config.Sentry.SENTRY_JAVA_SDK_NAME}\"")
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.withType<JavaCompile>().configureEach {
  dependsOn(tasks.generateBuildConfig)
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
  }
  // Vendored code is not checked. In io/sentry/vendor/otel/sql only the upstream files and the
  // generated lexer are excluded, so our own classes there are checked. Error Prone matches with
  // RE2J, which has no lookahead, hence the explicit list.
  options.errorprone.errorproneArgs.add(
    "-XepExcludedPaths:.*/io/sentry/vendor/([^/]+\\.java|gson/.*|otel/sql/(SqlQuery|SqlDialect|StringUtils|AutoSqlSanitizer)\\.java)",
  )
}

tasks.jar {
  from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
    into("META-INF")
    rename { "SENTRY_THIRD_PARTY_NOTICES.md" }
  }
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_JAVA_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
