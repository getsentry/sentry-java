package io.sentry.spring.boot.jakarta

import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.checkEvent
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.test.Test

class SpringProfilesEventProcessorTest {

    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java, WebMvcAutoConfiguration::class.java))
        .withUserConfiguration(MockTransportConfiguration::class.java)

    @Test
    fun `when default Spring profile is active, sets traceContext spring active_profiles to empty list on sent event`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MockTransportConfiguration::class.java)
            .run {
                Sentry.captureMessage("test")
                val transport = it.getBean(ITransport::class.java)
                verify(transport).send(
                    checkEvent { event ->
                        val traceContext = event.contexts.trace
                        assertThat(traceContext).isNotNull()
                        val traceData = traceContext!!.data
                        assertThat(traceData.get("spring.active_profiles")).isEqualTo(listOf<String>())
                    },
                    anyOrNull()
                )
            }
    }

    @Test
    fun `when non-default Spring profiles are active, sets traceContext spring active_profiles to array of profile names`() {
        contextRunner
            .withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MockTransportConfiguration::class.java)
            .withPropertyValues(
                "spring.profiles.active=test1,test2"
            )
            .run {
                Sentry.captureMessage("test")
                val transport = it.getBean(ITransport::class.java)
                verify(transport).send(
                    checkEvent { event ->
                        val traceContext = event.contexts.trace
                        assertThat(traceContext).isNotNull()
                        val traceData = traceContext!!.data
                        assertThat(traceData.get("spring.active_profiles")).isEqualTo(listOf("test1", "test2"))
                    },
                    anyOrNull()
                )
            }
    }

    @Configuration(proxyBeanMethods = false)
    open class MockTransportConfiguration {

        private val transport = mock<ITransport>()

        @Bean
        open fun mockTransportFactory(): ITransportFactory {
            val factory = mock<ITransportFactory>()
            whenever(factory.create(any(), any())).thenReturn(transport)
            return factory
        }

        @Bean
        open fun sentryTransport() = transport
    }
}
