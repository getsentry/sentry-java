package io.sentry.spring.boot4

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.micrometer.SentryMeterRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class SentryMicrometerConfigurationTest {
  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java))
      .withUserConfiguration(SentryAutoConfigurationTest.NoOpTransportConfiguration::class.java)
      .withPropertyValues(
        "sentry.dsn=http://key@localhost/proj",
        "sentry.shutdown-timeout-millis=0",
        "sentry.metrics.enabled=false",
      )

  @AfterTest
  fun tearDown() {
    Sentry.close()
  }

  @Test
  fun `integration is disabled by default and supports explicit opt out`() {
    contextRunner.run {
      assertThat(it).doesNotHaveBean(SentryMeterRegistry::class.java)
      assertThat(it.getBean(SentryProperties::class.java).micrometer.isEnabled).isFalse()
      assertThat(it.getBean(SentryProperties::class.java).micrometer.pollIntervalMillis)
        .isEqualTo(60_000)
    }
    contextRunner.withPropertyValues("sentry.micrometer.enabled=false").run {
      assertThat(it).doesNotHaveBean(SentryMeterRegistry::class.java)
    }
  }

  @Test
  fun `logging metrics are ignored by default before meter registration`() {
    contextRunner
      .withPropertyValues(
        "sentry.micrometer.enabled=true",
        "sentry.micrometer.poll-interval-millis=0",
      )
      .run {
        val registry = it.getBean(SentryMeterRegistry::class.java)
        assertThat(
            it.getBean(IScopes::class.java).options.metrics.ignoredMetrics?.map { it.filterString }
          )
          .containsExactly("logback[.]events", "log4j2[.]events")
        for (name in listOf("logback.events", "log4j2.events")) {
          val counter = registry.counter(name)
          counter.increment()
          assertThat(counter.count()).isZero()
          assertThat(registry.find(name).counter()).isNull()
        }
        for (name in listOf("business.operations", "logbackXevents", "log4j2Xevents")) {
          val counter = registry.counter(name)
          counter.increment()
          assertThat(counter.count()).isEqualTo(1.0)
        }
      }
  }

  @Test
  fun `empty ignored metrics property disables logging defaults`() {
    contextRunner
      .withPropertyValues("sentry.micrometer.enabled=true", "sentry.metrics.ignored-metrics=")
      .run {
        assertThat(it.getBean(IScopes::class.java).options.metrics.ignoredMetrics).isEmpty()
        val registry = it.getBean(SentryMeterRegistry::class.java)
        for (name in listOf("logback.events", "log4j2.events")) {
          val counter = registry.counter(name)
          counter.increment()
          assertThat(counter.count()).isEqualTo(1.0)
        }
      }
  }

  @Test
  fun `custom ignored metrics property replaces logging defaults`() {
    contextRunner
      .withPropertyValues(
        "sentry.micrometer.enabled=true",
        "sentry.metrics.ignored-metrics=business[.]operations",
      )
      .run {
        assertThat(
            it.getBean(IScopes::class.java).options.metrics.ignoredMetrics?.map { it.filterString }
          )
          .containsExactly("business[.]operations")
        val registry = it.getBean(SentryMeterRegistry::class.java)
        val business = registry.counter("business.operations")
        business.increment()
        assertThat(business.count()).isZero()
        for (name in listOf("logback.events", "log4j2.events")) {
          val counter = registry.counter(name)
          counter.increment()
          assertThat(counter.count()).isEqualTo(1.0)
        }
      }
  }

  @Test
  fun `options callback can append to logging defaults`() {
    contextRunner
      .withBean(
        Sentry.OptionsConfiguration::class.java,
        {
          Sentry.OptionsConfiguration<SentryOptions> { options ->
            options.metrics.addIgnoredMetric("business[.]operations")
          }
        },
      )
      .run {
        assertThat(
            it.getBean(IScopes::class.java).options.metrics.ignoredMetrics?.map { it.filterString }
          )
          .containsExactly("logback[.]events", "log4j2[.]events", "business[.]operations")
      }
  }

  @Test
  fun `options callback can replace or clear logging defaults`() {
    for (names in listOf(null, emptyList(), listOf("business[.]operations"))) {
      contextRunner
        .withPropertyValues("sentry.micrometer.enabled=true")
        .withBean(
          Sentry.OptionsConfiguration::class.java,
          {
            Sentry.OptionsConfiguration<SentryOptions> { options ->
              options.metrics.setIgnoredMetrics(names)
            }
          },
        )
        .run {
          assertThat(
              it.getBean(IScopes::class.java).options.metrics.ignoredMetrics?.map {
                it.filterString
              }
            )
            .isEqualTo(names)
          val registry = it.getBean(SentryMeterRegistry::class.java)
          for (name in listOf("logback.events", "log4j2.events")) {
            val counter = registry.counter(name)
            counter.increment()
            assertThat(counter.count()).isEqualTo(1.0)
          }
        }
    }
  }

  @Test
  fun `integration binds properties and depends on initialized Sentry scopes`() {
    var registry: SentryMeterRegistry? = null

    contextRunner
      .withPropertyValues(
        "sentry.micrometer.enabled=true",
        "sentry.micrometer.poll-interval-millis=0",
      )
      .run {
        assertThat(it).hasSingleBean(SentryMeterRegistry::class.java)
        assertThat(it).hasSingleBean(IScopes::class.java)
        assertThat(it.getBean(SentryProperties::class.java).micrometer.isEnabled).isTrue()
        assertThat(it.getBean(SentryProperties::class.java).micrometer.pollIntervalMillis)
          .isEqualTo(0)
        assertThat(
            it.sourceApplicationContext.beanFactory.getDependenciesForBean("sentryMeterRegistry")
          )
          .contains("sentryHub")
        registry = it.getBean(SentryMeterRegistry::class.java)
        assertThat(registry!!.isClosed).isFalse()
      }

    assertThat(registry!!.isClosed).isTrue()
  }

  @Test
  fun `integration is absent when Sentry auto-configuration is disabled`() {
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java))
      .withUserConfiguration(SentryAutoConfigurationTest.NoOpTransportConfiguration::class.java)
      .withPropertyValues("sentry.micrometer.enabled=true")
      .run { assertThat(it).doesNotHaveBean(SentryMeterRegistry::class.java) }
  }

  @Test
  fun `integration backs off for a user provided registry`() {
    contextRunner
      .withPropertyValues("sentry.micrometer.enabled=true")
      .withUserConfiguration(CustomRegistryConfiguration::class.java)
      .run {
        assertThat(it).hasSingleBean(SentryMeterRegistry::class.java)
        assertThat(it).hasBean("customSentryMeterRegistry")
        assertThat(it).doesNotHaveBean("sentryMeterRegistry")
      }
  }

  @Test
  fun `integration is absent when sentry micrometer is not on the classpath`() {
    contextRunner
      .withPropertyValues("sentry.micrometer.enabled=true")
      .withClassLoader(FilteredClassLoader(SentryMeterRegistry::class.java))
      .run { assertThat(it).doesNotHaveBean(SentryMeterRegistry::class.java) }
  }

  @Test
  fun `Spring primary registry forwards to Sentry and another registry`() {
    ApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          SentryAutoConfiguration::class.java,
          MetricsAutoConfiguration::class.java,
          CompositeMeterRegistryAutoConfiguration::class.java,
        )
      )
      .withUserConfiguration(
        SentryAutoConfigurationTest.NoOpTransportConfiguration::class.java,
        SimpleRegistryConfiguration::class.java,
      )
      .withPropertyValues(
        "sentry.dsn=http://key@localhost/proj",
        "sentry.shutdown-timeout-millis=0",
        "sentry.metrics.enabled=false",
        "sentry.micrometer.enabled=true",
        "sentry.micrometer.poll-interval-millis=0",
      )
      .run {
        val primary = it.getBean(MeterRegistry::class.java)
        val sentry = it.getBean(SentryMeterRegistry::class.java)
        val simple = it.getBean(SimpleMeterRegistry::class.java)

        assertThat(primary).isInstanceOf(CompositeMeterRegistry::class.java)
        primary.counter("requests").increment()

        assertThat(sentry.get("requests").counter().count()).isEqualTo(1.0)
        assertThat(simple.get("requests").counter().count()).isEqualTo(1.0)
        for (name in listOf("logback.events", "log4j2.events")) {
          primary.counter(name).increment()
          assertThat(sentry.find(name).counter()).isNull()
          assertThat(simple.get(name).counter().count()).isEqualTo(1.0)
        }
      }
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomRegistryConfiguration {
    @Bean open fun customSentryMeterRegistry() = SentryMeterRegistry(0)
  }

  @Configuration(proxyBeanMethods = false)
  open class SimpleRegistryConfiguration {
    @Bean open fun simpleMeterRegistry() = SimpleMeterRegistry()
  }
}
