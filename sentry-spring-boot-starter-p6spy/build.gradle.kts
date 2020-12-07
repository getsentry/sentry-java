import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.springBoot) version Config.springBootVersion apply false
}

apply(plugin = Config.BuildPlugins.springDependencyManagement)

the<DependencyManagementExtension>().apply {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    api(project(":sentry"))
    api(project(":sentry-spring"))
    api(project(":sentry-spring-boot-starter"))
    api(project(":sentry-p6spy"))
    api(Config.Libs.springBootStarter)
    api(Config.Libs.datasourceProxySpringBootStarter)
}
