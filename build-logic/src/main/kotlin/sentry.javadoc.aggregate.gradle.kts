import io.sentry.gradle.AggregateJavadoc
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named

val javadocConsumer by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("javadoc"))
    }
}

subprojects {
    javadocConsumer.dependencies.add(dependencies.create(this))
}

val javadocCollection = javadocConsumer.incoming.artifactView { lenient(true) }.files

tasks.register("aggregateJavadoc", AggregateJavadoc::class) {
    group = "documentation"
    description = "Aggregates Javadocs from all subprojects into a single directory."
    javadocFiles.set(javadocCollection)
    outputDir.set(layout.buildDirectory.dir("docs/javadoc"))
}
