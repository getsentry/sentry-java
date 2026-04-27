package io.sentry.spring.boot.jakarta

import io.sentry.kafka.SentryKafkaProducer
import io.sentry.opentelemetry.SentryAutoConfigurationCustomizerProvider
import io.sentry.spring.jakarta.kafka.SentryKafkaConsumerBeanPostProcessor
import io.sentry.spring.jakarta.kafka.SentryKafkaProducerBeanPostProcessor
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.core.KafkaTemplate

class SentryKafkaAutoConfigurationTest {

  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java))
      .withPropertyValues(
        "sentry.dsn=http://key@localhost/proj",
        "sentry.traces-sample-rate=1.0",
        "sentry.shutdownTimeoutMillis=0",
        "sentry.sessionFlushTimeoutMillis=0",
        "sentry.flushTimeoutMillis=0",
        "sentry.readTimeoutMillis=50",
        "sentry.connectionTimeoutMillis=50",
        "sentry.send-modules=false",
        "sentry.debug=false",
      )

  /** Hide the OTel customizer so conditions evaluate as "no OTel present". */
  private val noOtelClassLoader =
    FilteredClassLoader(SentryAutoConfigurationCustomizerProvider::class.java)

  private val noSentryKafkaClassLoader =
    FilteredClassLoader(
      SentryKafkaProducer::class.java,
      SentryAutoConfigurationCustomizerProvider::class.java,
    )

  private val noSpringKafkaClassLoader =
    FilteredClassLoader(
      KafkaTemplate::class.java,
      SentryAutoConfigurationCustomizerProvider::class.java,
    )

  @Test
  fun `registers Kafka BPPs when queue tracing is enabled`() {
    contextRunner
      .withClassLoader(noOtelClassLoader)
      .withPropertyValues("sentry.enable-queue-tracing=true")
      .run { context ->
        assertThat(context).hasSingleBean(SentryKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).hasSingleBean(SentryKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when queue tracing is disabled`() {
    contextRunner.withClassLoader(noOtelClassLoader).run { context ->
      assertThat(context).doesNotHaveBean(SentryKafkaProducerBeanPostProcessor::class.java)
      assertThat(context).doesNotHaveBean(SentryKafkaConsumerBeanPostProcessor::class.java)
    }
  }

  @Test
  fun `does not register Kafka BPPs when sentry-kafka is not present`() {
    contextRunner
      .withClassLoader(noSentryKafkaClassLoader)
      .withPropertyValues("sentry.enable-queue-tracing=true")
      .run { context ->
        assertThat(context).doesNotHaveBean(SentryKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).doesNotHaveBean(SentryKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when spring-kafka is not present`() {
    contextRunner
      .withClassLoader(noSpringKafkaClassLoader)
      .withPropertyValues("sentry.enable-queue-tracing=true")
      .run { context ->
        assertThat(context).doesNotHaveBean(SentryKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).doesNotHaveBean(SentryKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when queue tracing is explicitly false`() {
    contextRunner
      .withClassLoader(noOtelClassLoader)
      .withPropertyValues("sentry.enable-queue-tracing=false")
      .run { context ->
        assertThat(context).doesNotHaveBean(SentryKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).doesNotHaveBean(SentryKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when OpenTelemetry integration is present`() {
    contextRunner.withPropertyValues("sentry.enable-queue-tracing=true").run { context ->
      assertThat(context).doesNotHaveBean(SentryKafkaProducerBeanPostProcessor::class.java)
      assertThat(context).doesNotHaveBean(SentryKafkaConsumerBeanPostProcessor::class.java)
    }
  }
}
