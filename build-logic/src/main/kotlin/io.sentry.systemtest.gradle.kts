import org.gradle.api.tasks.ClasspathNormalizer

// The sample system tests launch the packaged app (war/shadowJar/bootJar) from
// build/libs as a separate process, so the archive is a real input even though it
// is not on the test classpath. See test/system-test-runner.py.
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
}
