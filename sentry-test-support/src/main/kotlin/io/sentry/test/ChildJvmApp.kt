package io.sentry.test

import io.sentry.time.Deadline
import io.sentry.time.JavaMonotonicTicker
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * An app running in a child JVM, for apps that must not share the test's JVM, such as one with the
 * OpenTelemetry agent attached or one whose exit is under test.
 *
 * A server app writes the port it listens on to the file named by the `PORTFILE` environment
 * variable, which Spring Boot's `WebServerPortFileWriter` reads too. The app's output goes to the
 * test's standard output as it arrives, so Gradle's test report shows it with the test that was
 * running.
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

  /** Waits for the app to exit on its own and returns its exit code. */
  fun awaitExit(timeoutSeconds: Long = 30): Int {
    check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
      "App did not exit within ${timeoutSeconds}s"
    }
    return process.exitValue()
  }

  override fun close() {
    process.destroyForcibly().waitFor()
    workDir.deleteRecursively()
  }

  companion object {
    private const val PORT_FILE = "port"

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
          .apply {
            environment().putAll(env)
            environment()["PORTFILE"] = File(workDir, PORT_FILE).path
          }
          .start()
      thread(isDaemon = true, name = "child-jvm-app-output") {
        try {
          process.inputStream.bufferedReader().forEachLine(::println)
        } catch (e: IOException) {
          // The app was killed while its output was being read.
        }
      }
      return ChildJvmApp(process, workDir)
    }
  }
}
