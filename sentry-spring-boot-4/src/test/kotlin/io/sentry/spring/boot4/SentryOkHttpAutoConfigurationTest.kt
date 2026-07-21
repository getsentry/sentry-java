package io.sentry.spring.boot4

import com.google.common.truth.Truth.assertThat
import io.sentry.ITransportFactory
import io.sentry.NoOpTransportFactory
import io.sentry.okhttp.SentryOkHttpEventListener
import io.sentry.okhttp.SentryOkHttpInterceptor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class SentryOkHttpAutoConfigurationTest {

  private val contextRunner =
    ApplicationContextRunner()
      .withUserConfiguration(TestApplication::class.java, NoOpTransportConfiguration::class.java)
      .withPropertyValues(
        "sentry.shutdownTimeoutMillis=0",
        "sentry.sessionFlushTimeoutMillis=0",
        "sentry.flushTimeoutMillis=0",
        "sentry.send-modules=false",
        "sentry.enable-backpressure-handling=false",
        "sentry.enable-spotlight=false",
      )

  @Test
  fun `instruments a Spring managed OkHttpClient`() {
    contextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)
        val existingInterceptor = context.getBean("existingInterceptor", Interceptor::class.java)

        assertThat(client.connectTimeoutMillis).isEqualTo(1234)
        assertThat(client.interceptors).contains(existingInterceptor)
        assertThat(client.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).hasSize(1)
        assertThat(client.interceptors.last()).isInstanceOf(SentryOkHttpInterceptor::class.java)

        val call = mock<Call>()
        whenever(call.request()).thenReturn(Request.Builder().url("https://example.com").build())
        val listener = client.eventListenerFactory.create(call)
        assertThat(listener).isInstanceOf(SentryOkHttpEventListener::class.java)

        listener.callStart(call)
        assertThat(context.getBean(RecordingEventListener::class.java).callStarted.get()).isTrue()
        listener.callEnd(call)
      }
  }

  @Test
  fun `instruments every Spring managed OkHttpClient`() {
    contextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(MultipleOkHttpClientsConfiguration::class.java)
      .run { context ->
        val clients = context.getBeansOfType(OkHttpClient::class.java)

        assertThat(clients).hasSize(2)
        clients.values.forEach { client ->
          assertThat(client.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).hasSize(1)
          assertThat(client.interceptors.last()).isInstanceOf(SentryOkHttpInterceptor::class.java)
        }
      }
  }

  @Test
  fun `does not duplicate an existing Sentry interceptor`() {
    contextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(ManuallyInstrumentedOkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        assertThat(client.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).hasSize(1)
        assertThat(client.eventListenerFactory.create(mock()))
          .isInstanceOf(SentryOkHttpEventListener::class.java)
      }
  }

  @Test
  fun `post processor is idempotent`() {
    val processor = SentryOkHttpClientBeanPostProcessor()
    val firstResult =
      processor.postProcessAfterInitialization(OkHttpClient(), "okHttpClient") as OkHttpClient
    val secondResult =
      processor.postProcessAfterInitialization(firstResult, "okHttpClient") as OkHttpClient

    assertThat(secondResult).isSameInstanceAs(firstResult)
    assertThat(secondResult.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).hasSize(1)
  }

  @Test
  fun `does not replace unrelated beans`() {
    val processor = SentryOkHttpClientBeanPostProcessor()
    val bean = Any()

    assertThat(processor.postProcessAfterInitialization(bean, "bean")).isSameInstanceAs(bean)
  }

  @Test
  fun `does not replace OkHttpClient subclasses`() {
    val processor = SentryOkHttpClientBeanPostProcessor()
    val client = CustomOkHttpClient()

    assertThat(processor.postProcessAfterInitialization(client, "okHttpClient"))
      .isSameInstanceAs(client)
    assertThat(client.interceptors).isEmpty()
  }

  @Test
  fun `does not instrument OkHttpClient without a dsn`() {
    contextRunner.withUserConfiguration(OkHttpClientConfiguration::class.java).run { context ->
      val client = context.getBean(OkHttpClient::class.java)

      assertThat(client.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).isEmpty()
      assertThat(client.eventListenerFactory.create(mock()))
        .isNotInstanceOf(SentryOkHttpEventListener::class.java)
    }
  }

  @Test
  fun `does not create a default OkHttpClient`() {
    contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj").run { context ->
      assertThat(context.getBeansOfType(OkHttpClient::class.java)).isEmpty()
    }
  }

  @Test
  fun `does not instrument when sentry-okhttp is not on the classpath`() {
    contextRunner
      .withClassLoader(FilteredClassLoader(SentryOkHttpInterceptor::class.java))
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)
        assertThat(client.interceptors).hasSize(1)
        assertThat(client.interceptors.first().javaClass.name)
          .isEqualTo(OkHttpClientConfiguration.ExistingInterceptor::class.java.name)
      }
  }

  @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration open class TestApplication

  @Configuration(proxyBeanMethods = false)
  open class NoOpTransportConfiguration {
    @Bean open fun noOpTransportFactory(): ITransportFactory = NoOpTransportFactory.getInstance()
  }

  @Configuration(proxyBeanMethods = false)
  open class OkHttpClientConfiguration {
    @Bean open fun existingInterceptor(): Interceptor = ExistingInterceptor()

    @Bean open fun recordingEventListener(): RecordingEventListener = RecordingEventListener()

    @Bean
    open fun okHttpClient(
      existingInterceptor: Interceptor,
      recordingEventListener: RecordingEventListener,
    ): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(1234, TimeUnit.MILLISECONDS)
        .addInterceptor(existingInterceptor)
        .eventListener(recordingEventListener)
        .build()

    class ExistingInterceptor : Interceptor {
      override fun intercept(chain: Interceptor.Chain) = chain.proceed(chain.request())
    }
  }

  @Configuration(proxyBeanMethods = false)
  open class MultipleOkHttpClientsConfiguration {
    @Bean open fun firstOkHttpClient(): OkHttpClient = OkHttpClient()

    @Bean open fun secondOkHttpClient(): OkHttpClient = OkHttpClient()
  }

  @Configuration(proxyBeanMethods = false)
  open class ManuallyInstrumentedOkHttpClientConfiguration {
    @Bean
    open fun okHttpClient(): OkHttpClient =
      OkHttpClient.Builder().addInterceptor(SentryOkHttpInterceptor()).build()
  }

  class CustomOkHttpClient : OkHttpClient()

  class RecordingEventListener : EventListener() {
    val callStarted = AtomicBoolean(false)

    override fun callStart(call: Call) {
      callStarted.set(true)
    }
  }
}
