package io.sentry.spring

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.Sentry
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import io.sentry.core.protocol.User
import io.sentry.core.transport.ITransport
import io.sentry.test.checkEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class SentryUserProviderEventProcessorIntegrationTest {

    @Test
    fun `when SentryUserProvider bean is configured, sets user provided user data`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfig::class.java, SentryUserProviderConfiguration::class.java))
            .run {
                val transport = it.getBean(ITransport::class.java)
                reset(transport)

                Sentry.captureMessage("test message")
                await.untilAsserted {
                    verify(transport).send(checkEvent { event: SentryEvent ->
                        assertThat(event.user).isNotNull
                        assertThat(event.user.username).isEqualTo("john.smith")
                    })
                }
            }
    }

    @Test
    fun `when SentryUserProvider bean is not configured, user data is not set`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfig::class.java))
            .run {
                val transport = it.getBean(ITransport::class.java)
                reset(transport)

                Sentry.captureMessage("test message")
                await.untilAsserted {
                    verify(transport).send(checkEvent { event: SentryEvent ->
                        assertThat(event.user).isNull()
                    })
                }
            }
    }

    @Test
    fun `when custom SentryUserProvider bean is configured, it's added after HttpServletRequestSentryUserProvider`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfig::class.java, SentryUserProviderConfiguration::class.java))
            .run {
                val options = it.getBean(SentryOptions::class.java)
                val userProviderEventProcessors = options.eventProcessors.filterIsInstance<SentryUserProviderEventProcessor>()
                assertEquals(2, userProviderEventProcessors.size)
                assertTrue(userProviderEventProcessors[0].sentryUserProvider is HttpServletRequestSentryUserProvider)
                assertTrue(userProviderEventProcessors[1].sentryUserProvider is CustomSentryUserProvider)
            }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    open class AppConfig {

        @Bean
        open fun mockTransport() = mock<ITransport>()
    }

    @Configuration
    open class SentryUserProviderConfiguration {

        @Bean
        open fun userProvider() = CustomSentryUserProvider()
    }

    open class CustomSentryUserProvider : SentryUserProvider {
        override fun provideUser(): User? {
            val user = User()
            user.username = "john.smith"
            return user
        }
    }
}
