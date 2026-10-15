package io.sentry.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Tells a test task's JVM where to find the built OpenTelemetry agent and the app to run with it.
 *
 * The test launches the app in a child JVM, so neither file is on the test classpath. Declaring
 * them here makes them task inputs: the test reruns when either changes and depends on the tasks
 * that build them.
 */
abstract class ChildJvmTestArguments : CommandLineArgumentProvider {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val agentJar: ConfigurableFileCollection

  @get:Classpath abstract val appClasspath: ConfigurableFileCollection

  override fun asArguments(): List<String> = buildList {
    add("-Dsentry.test.agentJar=${agentJar.singleFile.absolutePath}")
    if (!appClasspath.isEmpty) {
      add("-Dsentry.test.appClasspath=${appClasspath.asPath}")
    }
  }
}
