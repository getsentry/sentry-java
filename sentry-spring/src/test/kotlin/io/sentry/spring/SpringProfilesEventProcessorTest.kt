package io.sentry.spring

import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.checkEvent
import io.sentry.protocol.Spring
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import kotlin.test.Test

class SpringProfilesEventProcessorTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(AppConfiguration::class.java)
            .withUserConfiguration(SpringProfilesEventProcessorConfiguration::class.java)
            .withUserConfiguration(MockTransportConfiguration::class.java)

    @Test
    fun `when default Spring profile is active, sets active_profiles in Spring context to empty list on sent event`() {
        contextRunner
            .run {
                Sentry.captureMessage("test")
                val transport = it.getBean(ITransport::class.java)
                verify(transport).send(
                    checkEvent { event ->
                        val expected = Spring()
                        expected.activeProfiles = listOf<String>().toTypedArray()
                        assertThat(event.contexts.spring).isEqualTo(expected)
                    },
                    anyOrNull(),
                )
            }
    }

    @Test
    fun `when non-default Spring profiles are active, sets active profiles in Spring context to list of profile names`() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=test1,test2",
            ).run {
                Sentry.captureMessage("test")
                val transport = it.getBean(ITransport::class.java)
                verify(transport).send(
                    checkEvent { event ->
                        val expected = Spring()
                        expected.activeProfiles = listOf("test1", "test2").toTypedArray()
                        assertThat(event.contexts.spring).isEqualTo(expected)
                    },
                    anyOrNull(),
                )
            }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfiguration

    @Configuration(proxyBeanMethods = false)
    open class SpringProfilesEventProcessorConfiguration {
        @Bean
        open fun springProfilesEventProcessor(environment: Environment): SpringProfilesEventProcessor =
            SpringProfilesEventProcessor(environment)
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
