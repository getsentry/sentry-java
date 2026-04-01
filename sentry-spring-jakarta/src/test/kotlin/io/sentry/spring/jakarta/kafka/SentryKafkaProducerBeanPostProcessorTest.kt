package io.sentry.spring.jakarta.kafka

import io.sentry.IScopes
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

class SentryKafkaProducerBeanPostProcessorTest {

  @Test
  fun `wraps KafkaTemplate beans in SentryKafkaProducerWrapper`() {
    val producerFactory = mock<ProducerFactory<String, String>>()
    val kafkaTemplate = mock<KafkaTemplate<String, String>>()
    whenever(kafkaTemplate.producerFactory).thenReturn(producerFactory)
    whenever(kafkaTemplate.defaultTopic).thenReturn("")
    whenever(kafkaTemplate.messageConverter).thenReturn(mock())
    whenever(kafkaTemplate.micrometerTagsProvider).thenReturn(null)

    val processor = SentryKafkaProducerBeanPostProcessor()
    val result = processor.postProcessAfterInitialization(kafkaTemplate, "kafkaTemplate")

    assertTrue(result is SentryKafkaProducerWrapper<*, *>)
  }

  @Test
  fun `does not double-wrap SentryKafkaProducerWrapper`() {
    val producerFactory = mock<ProducerFactory<String, String>>()
    val kafkaTemplate = mock<KafkaTemplate<String, String>>()
    whenever(kafkaTemplate.producerFactory).thenReturn(producerFactory)
    whenever(kafkaTemplate.defaultTopic).thenReturn("")
    whenever(kafkaTemplate.messageConverter).thenReturn(mock())
    whenever(kafkaTemplate.micrometerTagsProvider).thenReturn(null)

    val scopes = mock<IScopes>()
    val alreadyWrapped = SentryKafkaProducerWrapper(kafkaTemplate, scopes)
    val processor = SentryKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(alreadyWrapped, "kafkaTemplate")

    assertSame(alreadyWrapped, result)
  }

  @Test
  fun `does not wrap non-KafkaTemplate beans`() {
    val someBean = "not a kafka template"
    val processor = SentryKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }
}
