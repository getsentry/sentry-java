# sentry-opentelemetry-bom

This BOM aligns Sentry OpenTelemetry modules with the OpenTelemetry artifacts tested by Sentry.

Use this BOM only when you want Sentry to manage OpenTelemetry dependency versions for Sentry's OpenTelemetry integrations. Do not import it for regular Sentry usage unless you also want this OpenTelemetry version alignment.

The BOM intentionally manages stable and `-alpha` OpenTelemetry artifacts, including incubator artifacts used by the OpenTelemetry instrumentation stack. It makes Sentry's tested OpenTelemetry versions authoritative, so verify dependency resolution before importing it if your application already uses newer OpenTelemetry versions.

This BOM is primarily for classpath-based OpenTelemetry integrations such as `sentry-opentelemetry-agentless`, `sentry-opentelemetry-agentless-spring`, `sentry-opentelemetry-otlp`, and `sentry-opentelemetry-otlp-spring`. It does not change the OpenTelemetry dependencies shaded into the `sentry-opentelemetry-agent` Java agent JAR.

## Dependency management ordering

Ordering matters when another BOM, such as Spring Boot's dependency management, also manages OpenTelemetry versions.

### Gradle

With Gradle's native dependency management, import the BOM as a platform and omit versions from Sentry OpenTelemetry and OpenTelemetry dependencies:

```kotlin
dependencies {
  implementation(platform("io.sentry:sentry-opentelemetry-bom:<sentry-version>"))

  implementation("io.sentry:sentry-opentelemetry-agentless")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
```

If another imported platform also manages OpenTelemetry versions, Gradle's normal version conflict resolution applies. Use `enforcedPlatform(...)` only when you need Sentry's tested OpenTelemetry versions to override other platforms.

When using Gradle with the Spring dependency management plugin, the last imported BOM wins. Import this BOM after Spring Boot's dependency management so its OpenTelemetry versions take precedence:

```kotlin
dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:<spring-boot-version>")
    mavenBom("io.sentry:sentry-opentelemetry-bom:<sentry-version>")
  }
}
```

If the Spring Boot Gradle plugin imports Spring Boot dependency management implicitly, add the Sentry BOM in your `dependencyManagement` block; explicit imports are applied after the implicit Spring Boot import.

### Maven

Maven uses different precedence rules: when multiple BOMs are imported in the same `<dependencyManagement>` block, the first declaration wins.

When using `spring-boot-starter-parent`, declare `sentry-opentelemetry-bom` in the child POM's `<dependencyManagement>` block. Dependency management in the child POM takes precedence over the parent:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.sentry</groupId>
      <artifactId>sentry-opentelemetry-bom</artifactId>
      <version>${sentry.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

When importing `spring-boot-dependencies` manually in the same POM, import `sentry-opentelemetry-bom` first so Sentry's OpenTelemetry versions win:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.sentry</groupId>
      <artifactId>sentry-opentelemetry-bom</artifactId>
      <version>${sentry.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>${spring-boot.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```
