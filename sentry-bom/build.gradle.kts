plugins {
    `java-platform` // used to build Maven BOM
    `maven-publish`
}

javaPlatform.allowDependencies()

dependencies {
    constraints {
        project.rootProject.subprojects
            .filter {
                !it.name.startsWith("sentry-samples") &&
                    it.name != project.name &&
                    !it.name.contains("test", ignoreCase = true) &&
                    it.name != "sentry-compose-helper"
            }
            .forEach { project ->
                evaluationDependsOn(project.path)
                project.publishing.publications
                    .mapNotNull { it as? MavenPublication }
                    .filter {
                        !it.artifactId.endsWith("-kotlinMultiplatform") &&
                            !it.artifactId.endsWith("-metadata")
                    }
                    .forEach {
                        val dependency = "${it.groupId}:${it.artifactId}:${it.version}"
                        api(dependency)
                    }
            }
    }
    api(platform(Config.Libs.OpenTelemetry.otelInstrumentationBom))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}
