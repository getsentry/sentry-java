import io.sentry.gradle.SystemTestExtension
import org.gradle.api.tasks.ClasspathNormalizer

val systemTest = extensions.create<SystemTestExtension>("sentrySystemTest")

// The sample system tests launch the packaged app (war/shadowJar/bootJar) from build/libs as a
// separate process, so the archive is a real input even though it is not on the test classpath.
// Agent-based samples are additionally launched with -javaagent:<otel agent>, another runtime
// input not on the classpath. See test/system-test-runner.py.
tasks.matching { it.name == "systemTest" }.configureEach {
  val archiveTask =
    listOf("war", "shadowJar", "bootJar").firstOrNull { it in tasks.names }
      ?: throw GradleException(
        "io.sentry.systemtest is applied to $path but none of war/shadowJar/bootJar " +
          "exist to provide the launched app archive for the systemTest task"
      )
  // Declaring the archive as an input also wires the dependency on its producing task.
  inputs
    .files(tasks.named(archiveTask))
    .withPropertyName("appArchive")
    .withNormalizer(ClasspathNormalizer::class.java)

  if (systemTest.usesOpenTelemetryAgent.get()) {
    // The runner builds the agent and launches the app with -javaagent before invoking this task,
    // so the agent jar is tracked for content only (by path, no cross-project task dependency): a
    // change to it makes systemTest out of date even though it runs outside the test JVM.
    val version = providers.gradleProperty("versionName").get()
    inputs
      .files(
        rootProject.layout.projectDirectory.file(
          "sentry-opentelemetry/sentry-opentelemetry-agent/build/libs/" +
            "sentry-opentelemetry-agent-$version.jar"
        )
      )
      .withPropertyName("openTelemetryAgent")
      .withNormalizer(ClasspathNormalizer::class.java)
  }
}
