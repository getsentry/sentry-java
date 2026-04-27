package io.sentry.spring.jakarta.kafka

import io.sentry.kafka.SentryKafkaProducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.kafka.clients.producer.Producer
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.core.ProducerPostProcessor

class SentryKafkaProducerBeanPostProcessorTest {

  @Test
  fun `registers Sentry post-processor on ProducerFactory`() {
    val factory = mock<ProducerFactory<String, String>>()
    whenever(factory.postProcessors).thenReturn(emptyList())
    val processor = SentryKafkaProducerBeanPostProcessor()

    processor.postProcessAfterInitialization(factory, "kafkaProducerFactory")

    val captor = argumentCaptor<ProducerPostProcessor<String, String>>()
    verify(factory).addPostProcessor(captor.capture())
    assertTrue(
      captor.firstValue is SentryKafkaProducerBeanPostProcessor.SentryProducerPostProcessor<*, *>
    )
  }

  @Test
  fun `is idempotent when Sentry post-processor is already registered`() {
    val factory = mock<ProducerFactory<String, String>>()
    val existing =
      SentryKafkaProducerBeanPostProcessor.SentryProducerPostProcessor<String, String>()
    whenever(factory.postProcessors).thenReturn(listOf(existing))
    val processor = SentryKafkaProducerBeanPostProcessor()

    processor.postProcessAfterInitialization(factory, "kafkaProducerFactory")

    verify(factory, never()).addPostProcessor(any())
  }

  @Test
  fun `does not modify non-ProducerFactory beans`() {
    val someBean = "not a producer factory"
    val processor = SentryKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }

  @Test
  fun `returns the same bean instance`() {
    val factory = mock<ProducerFactory<String, String>>()
    whenever(factory.postProcessors).thenReturn(emptyList())
    val processor = SentryKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(factory, "kafkaProducerFactory")

    assertSame(factory, result, "BPP must return the same bean, not a replacement")
  }

  @Test
  fun `registered post-processor wraps producers in SentryKafkaProducer`() {
    val pp = SentryKafkaProducerBeanPostProcessor.SentryProducerPostProcessor<String, String>()
    val raw = mock<Producer<String, String>>()

    val wrapped = pp.apply(raw)

    assertTrue(wrapped is SentryKafkaProducer<*, *>)
    assertSame(raw, (wrapped as SentryKafkaProducer<String, String>).delegate)
  }

  @Test
  fun `registered post-processor does not double-wrap`() {
    val pp = SentryKafkaProducerBeanPostProcessor.SentryProducerPostProcessor<String, String>()
    val raw = mock<Producer<String, String>>()
    val alreadyWrapped = SentryKafkaProducer(raw)

    val result = pp.apply(alreadyWrapped)

    assertSame(alreadyWrapped, result)
  }

  @Test
  fun `integrates with DefaultKafkaProducerFactory addPostProcessor contract`() {
    // Sanity check against the real Spring Kafka API surface — DefaultKafkaProducerFactory
    // honors addPostProcessor and exposes it via getPostProcessors().
    val factory = DefaultKafkaProducerFactory<String, String>(emptyMap())
    val processor = SentryKafkaProducerBeanPostProcessor()

    processor.postProcessAfterInitialization(factory, "kafkaProducerFactory")

    assertEquals(1, factory.postProcessors.size)
    assertTrue(
      factory.postProcessors.first()
        is SentryKafkaProducerBeanPostProcessor.SentryProducerPostProcessor<*, *>
    )
  }
}
