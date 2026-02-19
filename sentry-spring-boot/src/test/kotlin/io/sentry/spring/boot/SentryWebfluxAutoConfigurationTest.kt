package io.sentry.spring.boot

import io.sentry.ITransportFactory
import io.sentry.NoOpTransportFactory
import io.sentry.spring.webflux.SentryWebExceptionHandler
import io.sentry.spring.webflux.SentryWebFilter
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.scheduler.Schedulers

class SentryWebfluxAutoConfigurationTest {
  // Base context runner with performance optimizations
  private val baseContextRunner =
    ReactiveWebApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          WebFluxAutoConfiguration::class.java,
          SentryWebfluxAutoConfiguration::class.java,
          SentryAutoConfiguration::class.java,
        )
      )
      .withPropertyValues(
        // Speed up tests by reducing timeouts and disabling expensive operations
        "sentry.shutdownTimeoutMillis=0",
        "sentry.sessionFlushTimeoutMillis=0",
        "sentry.flushTimeoutMillis=0",
        "sentry.readTimeoutMillis=50",
        "sentry.connectionTimeoutMillis=50",
        "sentry.send-modules=false", // Disable expensive module sending
        "sentry.attach-stacktrace=false", // Disable expensive stacktrace collection
        "sentry.attach-threads=false", // Disable expensive thread info
        "sentry.enable-backpressure-handling=false",
        "sentry.enable-spotlight=false",
        "sentry.debug=false",
        "sentry.max-breadcrumbs=0", // Disable breadcrumb collection for performance
      )

  // Use the optimized base runner by default
  private val contextRunner =
    baseContextRunner.withUserConfiguration(
      NoOpTransportConfiguration::class.java
    ) // Use no-op transport to avoid network calls

  // Specialized context runner for tests requiring DSN
  private val dsnEnabledRunner =
    baseContextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(
        NoOpTransportConfiguration::class.java
      ) // Use no-op transport to avoid network calls

  @Test
  fun `configures sentryWebFilter`() {
    dsnEnabledRunner.run { assertThat(it).hasSingleBean(SentryWebFilter::class.java) }
  }

  @Test
  fun `configures exception handler`() {
    dsnEnabledRunner.run { assertThat(it).hasSingleBean(SentryWebExceptionHandler::class.java) }
  }

  @Test
  fun `does not run when reactor is not on the classpath`() {
    contextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(Schedulers::class.java))
      .run {
        assertThat(it).doesNotHaveBean(SentryWebExceptionHandler::class.java)
        assertThat(it).doesNotHaveBean(SentryWebFilter::class.java)
      }
  }

  @Test
  fun `does not run when dsn is not configured`() {
    contextRunner.withClassLoader(FilteredClassLoader(Schedulers::class.java)).run {
      assertThat(it).doesNotHaveBean(SentryWebExceptionHandler::class.java)
      assertThat(it).doesNotHaveBean(SentryWebFilter::class.java)
    }
  }

  @Configuration(proxyBeanMethods = false)
  open class NoOpTransportConfiguration {

    @Bean
    open fun noOpTransportFactory(): ITransportFactory {
      return NoOpTransportFactory.getInstance()
    }
  }
}
