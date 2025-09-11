plugins {
  `java-platform` // used to build Maven BOM
  `maven-publish`
}

dependencies {
  constraints {
    project.rootProject.subprojects
      .filter {
        !it.name.startsWith("sentry-samples") &&
          it.name != project.name &&
          !it.name.contains("test", ignoreCase = true) &&
          !it.name.contains("sentry-android-distribution")
      }
      .forEach { project ->
        evaluationDependsOn(project.path)
        project.publishing.publications
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
