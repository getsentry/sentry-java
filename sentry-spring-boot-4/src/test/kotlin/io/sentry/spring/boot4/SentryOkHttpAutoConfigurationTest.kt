package io.sentry.spring.boot4

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.OpenTelemetry
import io.sentry.ITransportFactory
import io.sentry.NoOpTransportFactory
import io.sentry.okhttp.SentryOkHttpEventListener
import io.sentry.okhttp.SentryOkHttpInterceptor
import io.sentry.opentelemetry.SentryAutoConfigurationCustomizerProvider
import io.sentry.opentelemetry.agent.AgentMarker
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

  private val baseContextRunner =
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

  private val contextRunner =
    baseContextRunner.withPropertyValues("sentry.clients.ok-http-enabled=true")

  private val noOtelClassLoader =
    FilteredClassLoader(
      SentryAutoConfigurationCustomizerProvider::class.java,
      AgentMarker::class.java,
    )

  private val noOtelContextRunner = contextRunner.withClassLoader(noOtelClassLoader)

  @Test
  fun `does not modify clients unless explicitly enabled`() {
    for (properties in
      listOf(emptyArray<String>(), arrayOf("sentry.clients.ok-http-enabled=false"))) {
      val plainClient = OkHttpClient()
      val manualClient =
        OkHttpClient.Builder()
          .addInterceptor(SentryOkHttpInterceptor())
          .eventListener(SentryOkHttpEventListener())
          .build()

      baseContextRunner
        .withClassLoader(noOtelClassLoader)
        .withPropertyValues("sentry.dsn=http://key@localhost/proj", *properties)
        .withBean("plainClient", OkHttpClient::class.java, { plainClient })
        .withBean("manualClient", OkHttpClient::class.java, { manualClient })
        .run { context ->
          assertThat(context.getBean("plainClient")).isSameInstanceAs(plainClient)
          assertThat(context.getBean("manualClient")).isSameInstanceAs(manualClient)
          assertThat(context.getBeansOfType(SentryOkHttpClientBeanPostProcessor::class.java))
            .isEmpty()
          assertThat(context.getBean(SentryProperties::class.java).clients.isOkHttpEnabled)
            .isFalse()
        }
    }
  }

  @Test
  fun `instruments a Spring managed OkHttpClient`() {
    noOtelContextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)
        val existingInterceptor = context.getBean("existingInterceptor", Interceptor::class.java)

        assertThat(context.getBean(SentryProperties::class.java).clients.isOkHttpEnabled).isTrue()
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
    noOtelContextRunner
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
  fun `forwards canceled before callStart to the original event listener`() {
    noOtelContextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        // OkHttp creates the listener in the Call constructor and reports canceled() even if
        // the call is never executed, i.e. before callStart() ever happens
        client.newCall(Request.Builder().url("https://example.com").build()).cancel()

        assertThat(context.getBean(RecordingEventListener::class.java).callCanceled.get()).isTrue()
      }
  }

  @Test
  fun `does not wrap a Sentry listener created by the original factory`() {
    val sentryListener = SentryOkHttpEventListener()
    val client = OkHttpClient.Builder().eventListenerFactory { sentryListener }.build()

    noOtelContextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withBean("okHttpClient", OkHttpClient::class.java, { client })
      .run { context ->
        val instrumented = context.getBean(OkHttpClient::class.java)

        assertThat(instrumented.eventListenerFactory.create(mock()))
          .isSameInstanceAs(sentryListener)
        assertThat(instrumented.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).hasSize(1)
      }
  }

  @Test
  fun `does not duplicate an existing Sentry interceptor`() {
    noOtelContextRunner
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
  fun `does not instrument when OpenTelemetry agent is present`() {
    contextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        assertThat(client.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).isEmpty()
        assertThat(client.eventListenerFactory.create(mock()))
          .isNotInstanceOf(SentryOkHttpEventListener::class.java)
      }
  }

  @Test
  fun `does not instrument when Sentry OpenTelemetry integration is present`() {
    contextRunner
      .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(
        OkHttpClientConfiguration::class.java,
        OpenTelemetryConfiguration::class.java,
      )
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        assertThat(client.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).isEmpty()
        assertThat(client.eventListenerFactory.create(mock()))
          .isNotInstanceOf(SentryOkHttpEventListener::class.java)
      }
  }

  @Test
  fun `does not instrument OkHttpClient without a dsn`() {
    noOtelContextRunner.withUserConfiguration(OkHttpClientConfiguration::class.java).run { context
      ->
      val client = context.getBean(OkHttpClient::class.java)

      assertThat(client.interceptors.filterIsInstance<SentryOkHttpInterceptor>()).isEmpty()
      assertThat(client.eventListenerFactory.create(mock()))
        .isNotInstanceOf(SentryOkHttpEventListener::class.java)
    }
  }

  @Test
  fun `does not create a default OkHttpClient`() {
    noOtelContextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj").run { context ->
      assertThat(context.getBeansOfType(OkHttpClient::class.java)).isEmpty()
    }
  }

  @Test
  fun `does not instrument when sentry-okhttp is not on the classpath`() {
    contextRunner
      .withClassLoader(
        FilteredClassLoader(
          SentryOkHttpInterceptor::class.java,
          SentryAutoConfigurationCustomizerProvider::class.java,
          AgentMarker::class.java,
        )
      )
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
  open class OpenTelemetryConfiguration {
    @Bean open fun openTelemetry(): OpenTelemetry = OpenTelemetry.noop()
  }

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
    val callCanceled = AtomicBoolean(false)

    override fun callStart(call: Call) {
      callStarted.set(true)
    }

    override fun canceled(call: Call) {
      callCanceled.set(true)
    }
  }
}
