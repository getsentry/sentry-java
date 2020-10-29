package io.sentry.spring.boot

import com.acme.MainBootClass
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.EventProcessor
import io.sentry.IHub
import io.sentry.Integration
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.User
import io.sentry.spring.HttpServletRequestSentryUserProvider
import io.sentry.spring.SentryUserProvider
import io.sentry.spring.SentryUserProviderEventProcessor
import io.sentry.spring.tracing.SentryTracingFilter
import io.sentry.test.checkEvent
import io.sentry.transport.ITransport
import io.sentry.transport.ITransportGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.info.GitProperties
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

class SentryAutoConfigurationTest {

    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java, WebMvcAutoConfiguration::class.java))

    @Test
    fun `hub is not created when auto-configuration dsn is not set`() {
        contextRunner
            .run {
                assertThat(it).doesNotHaveBean(IHub::class.java)
            }
    }

    @Test
    fun `hub is created when dsn is provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(IHub::class.java)
            }
    }

    @Test
    fun `OptionsConfiguration is created if custom one is not provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(Sentry.OptionsConfiguration::class.java)
            }
    }

    @Test
    fun `OptionsConfiguration is not created if custom one is provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomOptionsConfigurationConfiguration::class.java)
            .run {
                assertThat(it).hasSingleBean(Sentry.OptionsConfiguration::class.java)
                assertThat(it.getBean(Sentry.OptionsConfiguration::class.java, "customOptionsConfiguration")).isNotNull
            }
    }

    @Test
    fun `properties are applied to SentryOptions`() {
        contextRunner.withPropertyValues(
            "sentry.dsn=http://key@localhost/proj",
            "sentry.read-timeout-millis=10",
            "sentry.shutdown-timeout=20",
            "sentry.flush-timeout-millis=30",
            "sentry.debug=true",
            "sentry.diagnostic-level=INFO",
            "sentry.sentry-client-name=my-client",
            "sentry.max-breadcrumbs=100",
            "sentry.release=1.0.3",
            "sentry.environment=production",
            "sentry.sample-rate=0.2",
            "sentry.in-app-excludes[0]=org.springframework",
            "sentry.in-app-includes[0]=com.myapp",
            "sentry.dist=my-dist",
            "sentry.attach-threads=true",
            "sentry.attach-stacktrace=true",
            "sentry.server-name=host-001",
            "sentry.enable-tracing=true"
        ).run {
            val options = it.getBean(SentryOptions::class.java)
            assertThat(options.readTimeoutMillis).isEqualTo(10)
            assertThat(options.shutdownTimeout).isEqualTo(20)
            assertThat(options.flushTimeoutMillis).isEqualTo(30)
            assertThat(options.isDebug).isTrue()
            assertThat(options.diagnosticLevel).isEqualTo(SentryLevel.INFO)
            assertThat(options.maxBreadcrumbs).isEqualTo(100)
            assertThat(options.release).isEqualTo("1.0.3")
            assertThat(options.environment).isEqualTo("production")
            assertThat(options.sampleRate).isEqualTo(0.2)
            assertThat(options.inAppExcludes).containsOnly("org.springframework")
            assertThat(options.inAppIncludes).containsOnly("com.myapp")
            assertThat(options.dist).isEqualTo("my-dist")
            assertThat(options.isAttachThreads).isEqualTo(true)
            assertThat(options.isAttachStacktrace).isEqualTo(true)
            assertThat(options.serverName).isEqualTo("host-001")
            assertThat(options.isEnableTracing).isTrue()
        }
    }

    @Test
    fun `sets sentryClientName property on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it.getBean(SentryOptions::class.java).sentryClientName).isEqualTo("sentry.java.spring-boot")
            }
    }

    @Test
    fun `sets SDK version on sent events`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MockTransportConfiguration::class.java)
            .run {
                Sentry.captureMessage("Some message")
                val transport = it.getBean(ITransport::class.java)
                await.untilAsserted {
                    verify(transport).send(checkEvent { event ->
                        assertThat(event.sdk.version).isEqualTo(BuildConfig.VERSION_NAME)
                        assertThat(event.sdk.name).isEqualTo(BuildConfig.SENTRY_SPRING_BOOT_SDK_NAME)
                        assertThat(event.sdk.packages).anyMatch { pkg ->
                            pkg.name == "maven:sentry-spring-boot-starter" && pkg.version == BuildConfig.VERSION_NAME
                        }
                    })
                }
            }
    }

    @Test
    fun `registers beforeSendCallback on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomBeforeSendCallbackConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).beforeSend).isInstanceOf(CustomBeforeSendCallback::class.java)
            }
    }

    @Test
    fun `registers beforeBreadcrumbCallback on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomBeforeBreadcrumbCallbackConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).beforeBreadcrumb).isInstanceOf(CustomBeforeBreadcrumbCallback::class.java)
            }
    }

    @Test
    fun `registers event processor on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomEventProcessorConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).eventProcessors).anyMatch { processor -> processor.javaClass == CustomEventProcessor::class.java }
            }
    }

    @Test
    fun `registers transport gate on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomTransportGateConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).transportGate).isInstanceOf(CustomTransportGate::class.java)
            }
    }

    @Test
    fun `registers custom integration on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomIntegration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).integrations).anyMatch { integration -> integration.javaClass == CustomIntegration::class.java }
            }
    }

    @Test
    fun `sets release on SentryEvents if Git integration is configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MockTransportConfiguration::class.java, MockGitPropertiesConfiguration::class.java)
            .run {
                Sentry.captureMessage("Some message")
                val transport = it.getBean(ITransport::class.java)
                await.untilAsserted {
                    verify(transport).send(checkEvent { event ->
                        assertThat(event.release).isEqualTo("git-commit-id")
                    })
                }
            }
    }

    @Test
    fun `sets custom release on SentryEvents if release property is set and Git integration is configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.release=my-release")
            .withUserConfiguration(MockTransportConfiguration::class.java, MockGitPropertiesConfiguration::class.java)
            .run {
                Sentry.captureMessage("Some message")
                val transport = it.getBean(ITransport::class.java)
                await.untilAsserted {
                    verify(transport).send(checkEvent { event ->
                        assertThat(event.release).isEqualTo("my-release")
                    })
                }
            }
    }

    @Test
    fun `sets inAppIncludes on SentryOptions from a class annotated with @SpringBootApplication`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MainBootClass::class.java)
            .run {
                assertThat(it.getBean(SentryProperties::class.java).inAppIncludes)
                    .containsOnly("com.acme")
            }
    }

    @Test
    fun `when custom SentryUserProvider bean is configured, it's added after HttpServletRequestSentryUserProvider`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.send-default-pii=true")
            .withConfiguration(UserConfigurations.of(SentryUserProviderConfiguration::class.java))
            .run {
                val options = it.getBean(SentryProperties::class.java)
                val userProviderEventProcessors = options.eventProcessors.filterIsInstance<SentryUserProviderEventProcessor>()
                assertEquals(2, userProviderEventProcessors.size)
                assertTrue(userProviderEventProcessors[0].sentryUserProvider is HttpServletRequestSentryUserProvider)
                assertTrue(userProviderEventProcessors[1].sentryUserProvider is CustomSentryUserProvider)
            }
    }

    @Test
    fun `when custom SentryUserProvider bean with higher order is configured, it's added before HttpServletRequestSentryUserProvider`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.send-default-pii=true")
            .withConfiguration(UserConfigurations.of(SentryHighestOrderUserProviderConfiguration::class.java))
            .run {
                val options = it.getBean(SentryProperties::class.java)
                val userProviderEventProcessors = options.eventProcessors.filterIsInstance<SentryUserProviderEventProcessor>()
                assertEquals(2, userProviderEventProcessors.size)
                assertTrue(userProviderEventProcessors[0].sentryUserProvider is CustomSentryUserProvider)
                assertTrue(userProviderEventProcessors[1].sentryUserProvider is HttpServletRequestSentryUserProvider)
            }
    }

    @Test
    fun `when tracing is enabled, creates tracing filter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true")
            .run {
                assertThat(it).hasSingleBean(SentryTracingFilter::class.java)
            }
    }

    @Test
    fun `when tracing is disabled, does not create tracing filter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=false")
            .run {
                assertThat(it).doesNotHaveBean(SentryTracingFilter::class.java)
            }
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomOptionsConfigurationConfiguration {

        @Bean
        open fun customOptionsConfiguration() = Sentry.OptionsConfiguration<SentryOptions> {
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class MockTransportConfiguration {

        @Bean
        open fun sentryTransport() = mock<ITransport>()
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomBeforeSendCallbackConfiguration {

        @Bean
        open fun beforeSendCallback() = CustomBeforeSendCallback()
    }

    class CustomBeforeSendCallback : SentryOptions.BeforeSendCallback {
        override fun execute(event: SentryEvent, hint: Any?): SentryEvent? = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomBeforeBreadcrumbCallbackConfiguration {

        @Bean
        open fun beforeBreadcrumbCallback() = CustomBeforeBreadcrumbCallback()
    }

    class CustomBeforeBreadcrumbCallback : SentryOptions.BeforeBreadcrumbCallback {
        override fun execute(breadcrumb: Breadcrumb, hint: Any?): Breadcrumb? = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomEventProcessorConfiguration {

        @Bean
        open fun customEventProcessor() = CustomEventProcessor()
    }

    class CustomEventProcessor : EventProcessor {
        override fun process(event: SentryEvent?, hint: Any?) = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomIntegrationConfiguration {

        @Bean
        open fun customIntegration() = CustomIntegration()
    }

    class CustomIntegration : Integration {
        override fun register(hub: IHub?, options: SentryOptions?) {}
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomTransportGateConfiguration {

        @Bean
        open fun customTransportGate() = CustomTransportGate()
    }

    class CustomTransportGate : ITransportGate {
        override fun isConnected() = true
    }

    @Configuration(proxyBeanMethods = false)
    open class MockGitPropertiesConfiguration {

        @Bean
        open fun gitProperties(): GitProperties {
            val git = mock<GitProperties>()
            whenever(git.commitId).thenReturn("git-commit-id")
            return git
        }
    }

    @Configuration
    open class SentryUserProviderConfiguration {

        @Bean
        open fun userProvider() = CustomSentryUserProvider()
    }

    @Configuration
    open class SentryHighestOrderUserProviderConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
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
