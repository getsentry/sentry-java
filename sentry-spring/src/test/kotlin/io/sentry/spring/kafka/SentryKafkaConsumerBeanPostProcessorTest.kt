package io.sentry.spring.kafka

import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.RecordInterceptor

class SentryKafkaConsumerBeanPostProcessorTest {

  @Test
  fun `wraps ConcurrentKafkaListenerContainerFactory with SentryKafkaRecordInterceptor`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.consumerFactory = consumerFactory

    val processor = SentryKafkaConsumerBeanPostProcessor()
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    // Verify via reflection that the interceptor was set
    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    val interceptor = field.get(factory)
    assertTrue(interceptor is SentryKafkaRecordInterceptor<*, *>)
  }

  @Test
  fun `does not double-wrap when SentryKafkaRecordInterceptor already set`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.consumerFactory = consumerFactory

    val processor = SentryKafkaConsumerBeanPostProcessor()
    // First wrap
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    val firstInterceptor = field.get(factory)

    // Second wrap — should be idempotent
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")
    val secondInterceptor = field.get(factory)

    assertSame(firstInterceptor, secondInterceptor)
  }

  @Test
  fun `does not wrap non-factory beans`() {
    val someBean = "not a factory"
    val processor = SentryKafkaConsumerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }

  @Test
  fun `chains existing customer RecordInterceptor as delegate`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.consumerFactory = consumerFactory

    val customerInterceptor = RecordInterceptor<String, String> { record -> record }
    factory.setRecordInterceptor(customerInterceptor)

    val processor = SentryKafkaConsumerBeanPostProcessor()
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    val installed = field.get(factory)
    assertTrue(
      installed is SentryKafkaRecordInterceptor<*, *>,
      "expected SentryKafkaRecordInterceptor, got ${installed?.javaClass}",
    )

    val delegateField = SentryKafkaRecordInterceptor::class.java.getDeclaredField("delegate")
    delegateField.isAccessible = true
    assertSame(
      customerInterceptor,
      delegateField.get(installed),
      "customer interceptor must be preserved as delegate",
    )
  }

  @Test
  fun `skips installation when reflection fails and preserves customer interceptor`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.consumerFactory = consumerFactory
    val customerInterceptor = RecordInterceptor<String, String> { record -> record }
    factory.setRecordInterceptor(customerInterceptor)

    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    assertSame(customerInterceptor, field.get(factory))

    val processor = SentryKafkaConsumerBeanPostProcessor("missingRecordInterceptor")
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    assertSame(
      customerInterceptor,
      field.get(factory),
      "customer interceptor must remain installed when Sentry cannot read it",
    )
  }
}
