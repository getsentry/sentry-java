import io.sentry.gradle.AggregateJavadoc
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named

val javadocPublisher by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("javadoc"))
    }
}

subprojects {
    javadocPublisher.dependencies.add(dependencies.create(this))
}

val javadocCollection = javadocPublisher.incoming.artifactView { lenient(true) }.files

tasks.register("aggregateJavadocs", AggregateJavadoc::class) {
    group = "documentation"
    description = "Aggregates Javadocs from all subprojects into a single directory."
    javadocFiles.set(javadocCollection)
    rootDir.set(layout.projectDirectory)
    outputDir.set(layout.buildDirectory.dir("docs/javadoc"))
}
