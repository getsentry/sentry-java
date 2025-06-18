package io.sentry.spring

import io.sentry.EventProcessor
import io.sentry.IScopes
import io.sentry.ITransportFactory
import io.sentry.Integration
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import kotlin.test.Test

class EnableSentryTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfig::class.java))

    @Test
    fun `sets properties from environment on SentryOptions`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithDefaultSendPii::class.java))
            .run {
                assertThat(it).hasSingleBean(SentryOptions::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.dsn).isEqualTo("http://key@localhost/proj")
                assertThat(options.isSendDefaultPii).isTrue()
            }

        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithEmptyDsn::class.java))
            .run {
                assertThat(it).hasSingleBean(SentryOptions::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.dsn).isEmpty()
                assertThat(options.isSendDefaultPii).isFalse()
            }
    }

    @Test
    fun `sets client name and SDK version`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryOptions::class.java)
            val options = it.getBean(SentryOptions::class.java)
            assertThat(options.sentryClientName).isEqualTo("sentry.java.spring")
            assertThat(options.sdkVersion).isNotNull
            assertThat(options.sdkVersion!!.name).isEqualTo("sentry.java.spring")
            assertThat(options.sdkVersion!!.version).isEqualTo(BuildConfig.VERSION_NAME)
            assertThat(options.sdkVersion!!.packageSet.map { pkg -> pkg.name }).contains("maven:io.sentry:sentry-spring")
            assertThat(options.sdkVersion!!.integrationSet).contains("Spring")
        }
    }

    @Test
    fun `enables external configuration on SentryOptions`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryOptions::class.java)
            val options = it.getBean(SentryOptions::class.java)
            assertThat(options.isEnableExternalConfiguration).isTrue()
        }
    }

    @Test
    fun `creates Sentry Scopes`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(IScopes::class.java)
        }
    }

    @Test
    fun `creates SentryExceptionResolver`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryExceptionResolver::class.java)
            assertThat(it)
                .getBean(SentryExceptionResolver::class.java)
                .hasFieldOrPropertyWithValue("order", 1)
        }
    }

    @Test
    fun `creates SentryExceptionResolver with order set in the @EnableSentry annotation`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithExceptionResolverOrderIntegerMaxValue::class.java))
            .run {
                assertThat(it).hasSingleBean(SentryExceptionResolver::class.java)
                assertThat(it)
                    .getBean(SentryExceptionResolver::class.java)
                    .hasFieldOrPropertyWithValue("order", Integer.MAX_VALUE)
            }
    }

    @Test
    fun `configures custom TracesSamplerCallback`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithCustomTracesSamplerCallback::class.java))
            .run {
                val options = it.getBean(SentryOptions::class.java)
                val samplerCallback = it.getBean(SentryOptions.TracesSamplerCallback::class.java)
                assertThat(options.tracesSampler).isEqualTo(samplerCallback)
            }
    }

    @Test
    fun `configures custom TransportFactory`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithCustomTransportFactory::class.java))
            .run {
                val options = it.getBean(SentryOptions::class.java)
                val transportFactory = it.getBean(ITransportFactory::class.java)
                assertThat(options.transportFactory).isEqualTo(transportFactory)
            }
    }

    @Test
    fun `configures options with options configuration`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithCustomOptionsConfiguration::class.java))
            .run {
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.environment).isEqualTo("from-options-configuration")
            }
    }

    @Test
    fun `configures custom before send callback`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithCustomBeforeSendCallback::class.java))
            .run {
                val beforeSendCallback = it.getBean(SentryOptions.BeforeSendCallback::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.beforeSend).isEqualTo(beforeSendCallback)
            }
    }

    @Test
    fun `configures custom before breadcrumb callback`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithCustomBeforeBreadcrumbCallback::class.java))
            .run {
                val beforeBreadcrumbCallback = it.getBean(SentryOptions.BeforeBreadcrumbCallback::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.beforeBreadcrumb).isEqualTo(beforeBreadcrumbCallback)
            }
    }

    @Test
    fun `configures custom event processors`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithCustomEventProcessors::class.java))
            .run {
                val firstProcessor = it.getBean("firstProcessor", EventProcessor::class.java)
                val secondProcessor = it.getBean("secondProcessor", EventProcessor::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.eventProcessors).contains(firstProcessor, secondProcessor)
            }
    }

    @Test
    fun `configures custom integrations`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithCustomIntegrations::class.java))
            .run {
                val firstIntegration = it.getBean("firstIntegration", Integration::class.java)
                val secondIntegration = it.getBean("secondIntegration", Integration::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.integrations).contains(firstIntegration, secondIntegration)
            }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfig

    @EnableSentry(dsn = "")
    class AppConfigWithEmptyDsn

    @EnableSentry(dsn = "http://key@localhost/proj", sendDefaultPii = true)
    class AppConfigWithDefaultSendPii

    @EnableSentry(dsn = "http://key@localhost/proj", exceptionResolverOrder = Integer.MAX_VALUE)
    class AppConfigWithExceptionResolverOrderIntegerMaxValue

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfigWithCustomTracesSamplerCallback {
        @Bean
        fun tracesSampler(): SentryOptions.TracesSamplerCallback {
            return SentryOptions.TracesSamplerCallback {
                return@TracesSamplerCallback 1.0
            }
        }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfigWithCustomTransportFactory {
        @Bean
        fun transport() =
            mock<ITransportFactory>().also {
                whenever(it.create(any(), any())).thenReturn(mock<ITransport>())
            }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfigWithCustomOptionsConfiguration {
        @Bean
        fun optionsConfiguration() =
            Sentry.OptionsConfiguration<SentryOptions> {
                it.environment = "from-options-configuration"
            }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfigWithCustomBeforeSendCallback {
        @Bean
        fun beforeSendCallback() = mock<SentryOptions.BeforeSendCallback>()
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfigWithCustomBeforeBreadcrumbCallback {
        @Bean
        fun beforeBreadcrumbCallback() = mock<SentryOptions.BeforeBreadcrumbCallback>()
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfigWithCustomEventProcessors {
        @Bean
        fun firstProcessor() = mock<EventProcessor>()

        @Bean
        fun secondProcessor() = mock<EventProcessor>()
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfigWithCustomIntegrations {
        @Bean
        fun firstIntegration() = mock<Integration>()

        @Bean
        fun secondIntegration() = mock<Integration>()
    }
}
