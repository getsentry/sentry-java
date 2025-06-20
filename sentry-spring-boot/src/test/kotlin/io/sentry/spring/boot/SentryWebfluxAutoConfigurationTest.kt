package io.sentry.spring.boot

import io.sentry.spring.webflux.SentryWebExceptionHandler
import io.sentry.spring.webflux.SentryWebFilter
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import reactor.core.scheduler.Schedulers

class SentryWebfluxAutoConfigurationTest {
  private val contextRunner =
    ReactiveWebApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          WebFluxAutoConfiguration::class.java,
          SentryWebfluxAutoConfiguration::class.java,
          SentryAutoConfiguration::class.java,
        )
      )

  @Test
  fun `configures sentryWebFilter`() {
    contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj").run {
      assertThat(it).hasSingleBean(SentryWebFilter::class.java)
    }
  }

  @Test
  fun `configures exception handler`() {
    contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj").run {
      assertThat(it).hasSingleBean(SentryWebExceptionHandler::class.java)
    }
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
}
