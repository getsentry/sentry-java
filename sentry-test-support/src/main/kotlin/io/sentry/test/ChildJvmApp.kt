package io.sentry.test

import io.sentry.time.Deadline
import io.sentry.time.JavaMonotonicTicker
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * An app running in a child JVM, for apps that must not share the test's JVM, such as one with the
 * OpenTelemetry agent attached.
 *
 * The app writes the port it listens on to the file named by the `PORTFILE` environment variable,
 * which Spring Boot's `WebServerPortFileWriter` reads too. [close] kills the app and prints its
 * output, which otherwise only lives in a temporary file.
 */
class ChildJvmApp private constructor(private val process: Process, private val workDir: File) :
  Closeable {
  private val portFile = File(workDir, PORT_FILE)

  /** Waits until the app has written its port and returns it. */
  fun awaitPort(timeoutSeconds: Long = 60): Int {
    val deadline =
      Deadline.after(JavaMonotonicTicker.getInstance(), timeoutSeconds, TimeUnit.SECONDS)
    while (true) {
      // The app may not have finished writing the file yet, in which case it doesn't parse.
      val port = portFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
      if (port != null) {
        return port
      }
      check(process.isAlive) { "App exited with ${process.exitValue()}" }
      check(!deadline.hasPassed()) { "App did not start within ${timeoutSeconds}s" }
      Thread.sleep(100)
    }
  }

  override fun close() {
    process.destroyForcibly().waitFor()
    val log = File(workDir, LOG_FILE)
    if (log.exists()) {
      println(log.readText())
    }
    workDir.deleteRecursively()
  }

  companion object {
    private const val PORT_FILE = "port"
    private const val LOG_FILE = "app.log"

    fun start(
      mainClass: String,
      classpath: String,
      jvmArgs: List<String> = emptyList(),
      env: Map<String, String> = emptyMap(),
      args: List<String> = emptyList(),
    ): ChildJvmApp {
      val workDir = Files.createTempDirectory("child-jvm-app").toFile()
      val java = File(System.getProperty("java.home"), "bin/java").path
      val process =
        ProcessBuilder(listOf(java) + jvmArgs + listOf("-cp", classpath, mainClass) + args)
          .redirectErrorStream(true)
          .redirectOutput(File(workDir, LOG_FILE))
          .apply {
            environment().putAll(env)
            environment()["PORTFILE"] = File(workDir, PORT_FILE).path
          }
          .start()
      return ChildJvmApp(process, workDir)
    }
  }
}
