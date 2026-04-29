package io.sentry.spring.jakarta.kafka

import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.kafka.clients.producer.ProducerInterceptor
import org.mockito.kotlin.mock
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.CompositeProducerInterceptor

class SentryKafkaProducerBeanPostProcessorTest {

  private fun readInterceptor(template: KafkaTemplate<*, *>): Any? {
    val field = KafkaTemplate::class.java.getDeclaredField("producerInterceptor")
    field.isAccessible = true
    return field.get(template)
  }

  @Test
  fun `sets SentryProducerInterceptor on KafkaTemplate`() {
    val template = KafkaTemplate<String, String>(mock<ProducerFactory<String, String>>())
    val processor = SentryKafkaProducerBeanPostProcessor()

    processor.postProcessAfterInitialization(template, "kafkaTemplate")

    assertTrue(readInterceptor(template) is SentryProducerInterceptor<*, *>)
  }

  @Test
  fun `does not double-wrap when SentryProducerInterceptor already set`() {
    val template = KafkaTemplate<String, String>(mock<ProducerFactory<String, String>>())
    val processor = SentryKafkaProducerBeanPostProcessor()

    processor.postProcessAfterInitialization(template, "kafkaTemplate")
    val firstInterceptor = readInterceptor(template)

    processor.postProcessAfterInitialization(template, "kafkaTemplate")
    val secondInterceptor = readInterceptor(template)

    assertSame(firstInterceptor, secondInterceptor)
  }

  @Test
  fun `does not modify non-KafkaTemplate beans`() {
    val someBean = "not a kafka template"
    val processor = SentryKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }

  @Test
  fun `returns the same bean instance`() {
    val template = KafkaTemplate<String, String>(mock<ProducerFactory<String, String>>())
    val processor = SentryKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(template, "kafkaTemplate")

    assertSame(template, result, "BPP should return the same bean, not a replacement")
  }

  @Test
  fun `composes with existing customer interceptor using CompositeProducerInterceptor`() {
    val template = KafkaTemplate<String, String>(mock<ProducerFactory<String, String>>())
    val customerInterceptor = mock<ProducerInterceptor<String, String>>()
    template.setProducerInterceptor(customerInterceptor)

    val processor = SentryKafkaProducerBeanPostProcessor()
    processor.postProcessAfterInitialization(template, "kafkaTemplate")

    assertTrue(
      readInterceptor(template) is CompositeProducerInterceptor<*, *>,
      "Should use CompositeProducerInterceptor when existing interceptor is present",
    )
  }
}
