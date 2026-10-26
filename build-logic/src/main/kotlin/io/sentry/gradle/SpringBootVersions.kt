package io.sentry.gradle

/**
 * The Spring Boot versions the Spring integration suites run on, per major line. A WAR suite runs
 * on the same versions as its Boot module, because the Boot BOM decides its Spring version.
 */
object SpringBootVersions {
  val BOOT_2 = listOf("2.7.0", "2.7.18")
  val BOOT_3 = listOf("3.2.12", "3.3.13", "3.4.13", "3.5.13")
  val BOOT_4 = listOf("4.0.0", "4.0.5", "4.1.0")
}
