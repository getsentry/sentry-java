plugins {
  `java-platform`
  `maven-publish`
}

javaPlatform.allowDependencies()

dependencies {
  api(platform(libs.otel.bom))
  api(platform(libs.otel.alpha.bom))
  api(platform(libs.otel.instrumentation.bom))
  api(platform(libs.otel.instrumentation.alpha.bom))

  constraints {
    project.rootProject.subprojects
      .filter {
        it.path.startsWith(":sentry-opentelemetry:") &&
          it.name != project.name &&
          !it.name.contains("test", ignoreCase = true)
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
