plugins {
  `java-platform` // used to build Maven BOM
  `maven-publish`
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
  id("io.sentry.publish")
}

dependencies {
  constraints {
    project.rootProject.subprojects
      .filter {
        !it.name.startsWith("sentry-samples") &&
          it.name != project.name &&
          !it.name.endsWith("-bom") &&
          !it.name.contains("test", ignoreCase = true) &&
          !it.name.contains("sentry-android-distribution")
      }
      .forEach { project ->
        evaluationDependsOn(project.path)
        // container projects such as sentry-opentelemetry have no build file and publish nothing
        val publishing = project.extensions.findByType<PublishingExtension>() ?: return@forEach
        publishing.publications
          .mapNotNull { it as? MavenPublication }
          .filter {
            !it.artifactId.endsWith("-kotlinMultiplatform") && !it.artifactId.endsWith("-metadata")
          }
          .forEach {
            val dependency = "${it.groupId}:${it.artifactId}:${it.version}"
            api(dependency)
          }
      }
  }
}
