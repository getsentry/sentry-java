package io.sentry.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.base.TestingExtension

private const val AGENT_FIXTURE = "agentFixture"
private const val OTEL_AGENT_JAR = "otelAgentJar"
private const val SUITE = "integrationTestOtelAgent"
private const val WITHOUT_AUTO_INIT = "${SUITE}WithoutAutoInit"

/**
 * Runs a Spring Boot module's OpenTelemetry agent tests against an app in a child JVM, with the
 * built agent attached.
 *
 * The agent must not run in the test's JVM, because it would instrument the test's own HTTP client
 * and server. So the app lives in the `agentFixture` source set and runs on its own classpath, like
 * a real app. The `integrationTestOtelAgent` suite's task runs the tests with the agent
 * initializing Sentry, and `integrationTestOtelAgentWithoutAutoInit` runs them with the Spring Boot
 * starter doing it. Each test JVM starts one app, so the second mode needs a task of its own, which
 * Gradle runs in parallel with the suite's.
 *
 * The module adds the app's and the suite's dependencies, which differ per Spring Boot line.
 */
class OtelAgentTestsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(JavaPlugin::class.java)
    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val agentFixture = sourceSets.create(AGENT_FIXTURE)

    // A plain project dependency on the agent module resolves to its unshadowed jar, which holds
    // only AgentMarker. The agent itself is the shadowed jar, which already bundles its
    // dependencies.
    project.configurations.create(OTEL_AGENT_JAR) {
      isCanBeResolved = true
      isCanBeConsumed = false
      isTransitive = false
      attributes {
        attribute(
          Usage.USAGE_ATTRIBUTE,
          project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
        )
        attribute(
          Bundling.BUNDLING_ATTRIBUTE,
          project.objects.named(Bundling::class.java, Bundling.SHADOWED),
        )
      }
    }
    project.dependencies.add(
      OTEL_AGENT_JAR,
      project.dependencies.project(
        mapOf("path" to ":sentry-opentelemetry:sentry-opentelemetry-agent")
      ),
    )

    val testing = project.extensions.getByType(TestingExtension::class.java)
    testing.suites.register(SUITE, JvmTestSuite::class.java) {
      useJUnit()
      targets.all {
        testTask.configure { runAgentFixture(autoInit = true, agentFixture.runtimeClasspath) }
      }
    }

    project.tasks.register(WITHOUT_AUTO_INIT, Test::class.java) {
      description = "Runs the OpenTelemetry agent tests with SENTRY_AUTO_INIT=false."
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      val sources = sourceSets.getByName(SUITE)
      testClassesDirs = sources.output.classesDirs
      classpath = sources.runtimeClasspath
      useJUnit()
      runAgentFixture(autoInit = false, agentFixture.runtimeClasspath)
    }
  }
}

/** Registers both OpenTelemetry agent test tasks on each Spring Boot version of the matrix. */
fun SpringBootMatrixExtension.otelAgentTests() {
  test(SUITE) { version ->
    runAgentFixture(autoInit = true, runtimeClasspath(AGENT_FIXTURE, version))
  }
  test(SUITE, name = WITHOUT_AUTO_INIT) { version ->
    runAgentFixture(autoInit = false, runtimeClasspath(AGENT_FIXTURE, version))
  }
}

/**
 * Has the tests start the app on [appClasspath] in a child JVM, with the built agent attached.
 * [autoInit] decides whether the agent initializes Sentry or, as with `SENTRY_AUTO_INIT=false`, the
 * Spring Boot starter.
 */
private fun Test.runAgentFixture(autoInit: Boolean, appClasspath: FileCollection) {
  jvmArgumentProviders.add(
    project.objects.newInstance(ChildJvmTestArguments::class.java).apply {
      agentJar.from(project.configurations.getByName(OTEL_AGENT_JAR))
      this.appClasspath.from(appClasspath)
    }
  )
  systemProperty("sentry.test.agentAutoInit", autoInit)
}
