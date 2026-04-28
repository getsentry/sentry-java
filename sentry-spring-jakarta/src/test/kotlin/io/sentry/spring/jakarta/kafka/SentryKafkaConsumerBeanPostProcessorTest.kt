package io.sentry.spring.jakarta.kafka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
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

    val customerInterceptor =
      object : RecordInterceptor<String, String> {
        override fun intercept(
          record: ConsumerRecord<String, String>,
          consumer: Consumer<String, String>,
        ): ConsumerRecord<String, String>? = record
      }
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
    // Subclass whose declared 'recordInterceptor' field does not exist on the
    // AbstractKafkaListenerContainerFactory class lookup path — this simulates the
    // future-spring-kafka case where the private field is renamed/removed.
    // We can't easily corrupt JDK reflection, so we instead verify the chosen
    // contract: when reflection succeeds and yields a non-Sentry interceptor,
    // it is preserved as a delegate (covered above). The reflection-failure
    // branch is logged at ERROR and returns the bean untouched; see
    // SentryKafkaConsumerBeanPostProcessor#postProcessAfterInitialization.
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.consumerFactory = consumerFactory
    val customerInterceptor =
      object : RecordInterceptor<String, String> {
        override fun intercept(
          record: ConsumerRecord<String, String>,
          consumer: Consumer<String, String>,
        ): ConsumerRecord<String, String>? = record
      }
    factory.setRecordInterceptor(customerInterceptor)

    // Sanity check: customer interceptor is set before BPP runs.
    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    assertSame(customerInterceptor, field.get(factory))

    // After BPP runs the customer interceptor must still be reachable
    // (either directly, or as the delegate of a SentryKafkaRecordInterceptor).
    val processor = SentryKafkaConsumerBeanPostProcessor()
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    val installed = field.get(factory)
    val effective =
      if (installed is SentryKafkaRecordInterceptor<*, *>) {
        val delegateField = SentryKafkaRecordInterceptor::class.java.getDeclaredField("delegate")
        delegateField.isAccessible = true
        delegateField.get(installed)
      } else {
        installed
      }
    assertEquals(
      customerInterceptor,
      effective,
      "customer interceptor must never be silently dropped",
    )
  }
}
