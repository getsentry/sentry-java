package io.sentry.spring.boot.jakarta

import io.sentry.spring.jakarta.kafka.SentryKafkaConsumerBeanPostProcessor
import io.sentry.spring.jakarta.kafka.SentryKafkaProducerBeanPostProcessor
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

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

  @Test
  fun `registers Kafka BPPs when queue tracing is enabled`() {
    contextRunner.withPropertyValues("sentry.enable-queue-tracing=true").run { context ->
      assertThat(context).hasSingleBean(SentryKafkaProducerBeanPostProcessor::class.java)
      assertThat(context).hasSingleBean(SentryKafkaConsumerBeanPostProcessor::class.java)
    }
  }

  @Test
  fun `does not register Kafka BPPs when queue tracing is disabled`() {
    contextRunner.run { context ->
      assertThat(context).doesNotHaveBean(SentryKafkaProducerBeanPostProcessor::class.java)
      assertThat(context).doesNotHaveBean(SentryKafkaConsumerBeanPostProcessor::class.java)
    }
  }

  @Test
  fun `does not register Kafka BPPs when queue tracing is explicitly false`() {
    contextRunner.withPropertyValues("sentry.enable-queue-tracing=false").run { context ->
      assertThat(context).doesNotHaveBean(SentryKafkaProducerBeanPostProcessor::class.java)
      assertThat(context).doesNotHaveBean(SentryKafkaConsumerBeanPostProcessor::class.java)
    }
  }
}
