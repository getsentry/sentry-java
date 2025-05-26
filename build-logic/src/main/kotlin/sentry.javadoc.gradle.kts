val javadocConfig : Configuration by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true

    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("javadoc"))
    }
}

tasks.withType<Javadoc>().configureEach {
    setDestinationDir(project.layout.buildDirectory.file("docs/javadoc").get().asFile)
    title = "${project.name} $version API"
    val opts = options as StandardJavadocDocletOptions
    opts.quiet()
    opts.encoding = "UTF-8"
    opts.memberLevel = JavadocMemberLevel.PROTECTED
    opts.stylesheetFile(rootProject.project.layout.projectDirectory.file("docs/stylesheet.css").asFile)
    opts.links = listOf(
        "https://docs.oracle.com/javase/8/docs/api/",
        "https://docs.spring.io/spring-framework/docs/current/javadoc-api/",
        "https://docs.spring.io/spring-boot/docs/current/api/"
    )
}

artifacts {
    add(javadocConfig.name, tasks.named("javadoc"))
}
