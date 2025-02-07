package io.sentry.spring.boot

import com.acme.MainBootClass
import io.opentelemetry.api.OpenTelemetry
import io.sentry.AsyncHttpTransportFactory
import io.sentry.Breadcrumb
import io.sentry.EventProcessor
import io.sentry.FilterString
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ITransportFactory
import io.sentry.Integration
import io.sentry.NoOpTransportFactory
import io.sentry.SamplingContext
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.checkEvent
import io.sentry.opentelemetry.SentryAutoConfigurationCustomizerProvider
import io.sentry.opentelemetry.agent.AgentMarker
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.quartz.SentryJobListener
import io.sentry.spring.ContextTagsEventProcessor
import io.sentry.spring.HttpServletRequestSentryUserProvider
import io.sentry.spring.SentryExceptionResolver
import io.sentry.spring.SentryUserFilter
import io.sentry.spring.SentryUserProvider
import io.sentry.spring.SpringSecuritySentryUserProvider
import io.sentry.spring.tracing.SentryTracingFilter
import io.sentry.spring.tracing.SpringServletTransactionNameProvider
import io.sentry.spring.tracing.TransactionNameProvider
import io.sentry.transport.ITransport
import io.sentry.transport.ITransportGate
import io.sentry.transport.apache.ApacheHttpClientTransportFactory
import org.aspectj.lang.ProceedingJoinPoint
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobListener
import org.quartz.Scheduler
import org.quartz.core.QuartzScheduler
import org.slf4j.MDC
import org.springframework.aop.support.NameMatchMethodPointcut
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.info.GitProperties
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.assertj.ApplicationContextAssert
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.servlet.HandlerExceptionResolver
import java.lang.RuntimeException
import javax.servlet.Filter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryAutoConfigurationTest {

    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java, WebMvcAutoConfiguration::class.java))

    @Test
    fun `scopes is not created when auto-configuration dsn is not set`() {
        contextRunner
            .run {
                assertThat(it).doesNotHaveBean(IScopes::class.java)
            }
    }

    @Test
    fun `scopes is created when dsn is provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(IScopes::class.java)
            }
    }

    @Test
    fun `OptionsConfiguration is created if custom one with name sentryOptionsConfiguration is not provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(Sentry.OptionsConfiguration::class.java)
            }
    }

    @Test
    fun `OptionsConfiguration with name sentryOptionsConfiguration is created if another one with different name is provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomOptionsConfigurationConfiguration::class.java)
            .run {
                assertThat(it).getBeans(Sentry.OptionsConfiguration::class.java).hasSize(2)
                assertThat(it).getBean("sentryOptionsConfiguration")
                    .isNotNull()
                    .isInstanceOf(Sentry.OptionsConfiguration::class.java)
                assertThat(it).getBean("customOptionsConfiguration")
                    .isNotNull()
                    .isInstanceOf(Sentry.OptionsConfiguration::class.java)
            }
    }

    @Test
    fun `sentryOptionsConfiguration bean is configured before custom OptionsConfiguration`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomOptionsConfigurationConfiguration::class.java)
            .run {
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.beforeSend).isNull()
            }
    }

    @Test
    fun `OptionsConfiguration is not created if custom one with name sentryOptionsConfiguration is provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(OverridingOptionsConfigurationConfiguration::class.java)
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
            "sentry.shutdown-timeout-millis=20",
            "sentry.flush-timeout-millis=30",
            "sentry.debug=true",
            "sentry.diagnostic-level=INFO",
            "sentry.sentry-client-name=my-client",
            "sentry.max-breadcrumbs=100",
            "sentry.release=1.0.3",
            "sentry.environment=production",
            "sentry.sample-rate=0.2",
            "sentry.in-app-includes=org.springframework,com.myapp",
            "sentry.in-app-excludes=org.jboss,com.microsoft",
            "sentry.dist=my-dist",
            "sentry.attach-threads=true",
            "sentry.attach-stacktrace=true",
            "sentry.server-name=host-001",
            "sentry.exception-resolver-order=100",
            "sentry.proxy.host=example.proxy.com",
            "sentry.proxy.port=8090",
            "sentry.proxy.user=proxy-user",
            "sentry.proxy.pass=proxy-pass",
            "sentry.traces-sample-rate=0.3",
            "sentry.tags.tag1=tag1-value",
            "sentry.tags.tag2=tag2-value",
            "sentry.ignored-exceptions-for-type=java.lang.RuntimeException,java.lang.IllegalStateException,io.sentry.Sentry",
            "sentry.trace-propagation-targets=localhost,^(http|https)://api\\..*\$",
            "sentry.enabled=false",
            "sentry.send-modules=false",
            "sentry.ignored-checkins=slug1,slugB",
            "sentry.ignored-errors=Some error,Another .*",
            "sentry.ignored-transactions=transactionName1,transactionNameB",
            "sentry.enable-backpressure-handling=false",
            "sentry.enable-spotlight=true",
            "sentry.spotlight-connection-url=http://local.sentry.io:1234",
            "sentry.force-init=true",
            "sentry.global-hub-mode=true",
            "sentry.cron.default-checkin-margin=10",
            "sentry.cron.default-max-runtime=30",
            "sentry.cron.default-timezone=America/New_York",
            "sentry.cron.default-failure-issue-threshold=40",
            "sentry.cron.default-recovery-threshold=50"
        ).run {
            val options = it.getBean(SentryProperties::class.java)
            assertThat(options.readTimeoutMillis).isEqualTo(10)
            assertThat(options.shutdownTimeoutMillis).isEqualTo(20)
            assertThat(options.flushTimeoutMillis).isEqualTo(30)
            assertThat(options.isDebug).isTrue()
            assertThat(options.diagnosticLevel).isEqualTo(SentryLevel.INFO)
            assertThat(options.maxBreadcrumbs).isEqualTo(100)
            assertThat(options.release).isEqualTo("1.0.3")
            assertThat(options.environment).isEqualTo("production")
            assertThat(options.sampleRate).isEqualTo(0.2)
            assertThat(options.inAppIncludes).containsOnly("org.springframework", "com.myapp")
            assertThat(options.inAppExcludes).containsOnly("com.microsoft", "org.jboss")
            assertThat(options.dist).isEqualTo("my-dist")
            assertThat(options.isAttachThreads).isEqualTo(true)
            assertThat(options.isAttachStacktrace).isEqualTo(true)
            assertThat(options.serverName).isEqualTo("host-001")
            assertThat(options.exceptionResolverOrder).isEqualTo(100)
            assertThat(options.proxy).isNotNull
            assertThat(options.proxy!!.host).isEqualTo("example.proxy.com")
            assertThat(options.proxy!!.port).isEqualTo("8090")
            assertThat(options.proxy!!.user).isEqualTo("proxy-user")
            assertThat(options.proxy!!.pass).isEqualTo("proxy-pass")
            assertThat(options.tracesSampleRate).isEqualTo(0.3)
            assertThat(options.tags).containsEntry("tag1", "tag1-value").containsEntry("tag2", "tag2-value")
            assertThat(options.ignoredExceptionsForType).containsOnly(RuntimeException::class.java, IllegalStateException::class.java)
            assertThat(options.tracePropagationTargets).containsOnly("localhost", "^(http|https)://api\\..*\$")
            assertThat(options.isEnabled).isEqualTo(false)
            assertThat(options.isSendModules).isEqualTo(false)
            assertThat(options.ignoredCheckIns).containsOnly(FilterString("slug1"), FilterString("slugB"))
            assertThat(options.ignoredErrors).containsOnly(FilterString("Some error"), FilterString("Another .*"))
            assertThat(options.ignoredTransactions).containsOnly(FilterString("transactionName1"), FilterString("transactionNameB"))
            assertThat(options.isEnableBackpressureHandling).isEqualTo(false)
            assertThat(options.isForceInit).isEqualTo(true)
            assertThat(options.isGlobalHubMode).isEqualTo(true)
            assertThat(options.isEnableSpotlight).isEqualTo(true)
            assertThat(options.spotlightConnectionUrl).isEqualTo("http://local.sentry.io:1234")
            assertThat(options.cron).isNotNull
            assertThat(options.cron!!.defaultCheckinMargin).isEqualTo(10L)
            assertThat(options.cron!!.defaultMaxRuntime).isEqualTo(30L)
            assertThat(options.cron!!.defaultTimezone).isEqualTo("America/New_York")
            assertThat(options.cron!!.defaultFailureIssueThreshold).isEqualTo(40L)
            assertThat(options.cron!!.defaultRecoveryThreshold).isEqualTo(50L)
        }
    }

    @Test
    fun `when tracePropagationTargets are not set, default is returned`() {
        contextRunner.withPropertyValues(
            "sentry.dsn=http://key@localhost/proj"
        ).run {
            val options = it.getBean(SentryProperties::class.java)
            assertThat(options.tracePropagationTargets).isNotNull().containsOnly(".*")
        }
    }

    @Test
    fun `when tracePropagationTargets property is set to empty list, empty list is returned`() {
        contextRunner.withPropertyValues(
            "sentry.dsn=http://key@localhost/proj",
            "sentry.trace-propagation-targets="
        ).run {
            val options = it.getBean(SentryProperties::class.java)
            assertThat(options.tracePropagationTargets).isNotNull().isEmpty()
        }
    }

    @Test
    fun `when traces sample rate is set to null and tracing is enabled, traces sample rate should be set to 0`() {
        contextRunner.withPropertyValues(
            "sentry.dsn=http://key@localhost/proj"
        ).run {
            val options = it.getBean(SentryProperties::class.java)
            assertThat(options.tracesSampleRate).isNull()
        }
    }

    @Test
    fun `when traces sample rate is set to a value and tracing is enabled, traces sample rate should not be overwritten`() {
        contextRunner.withPropertyValues(
            "sentry.dsn=http://key@localhost/proj",
            "sentry.traces-sample-rate=0.3"
        ).run {
            val options = it.getBean(SentryProperties::class.java)
            assertThat(options.tracesSampleRate).isNotNull().isEqualTo(0.3)
        }
    }

    @Test
    fun `sets sentryClientName property on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it.getBean(SentryOptions::class.java).sentryClientName).isEqualTo("sentry.java.spring-boot/${BuildConfig.VERSION_NAME}")
            }
    }

    @Test
    fun `sets SDK version on sent events`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MockTransportConfiguration::class.java)
            .run {
                Sentry.captureMessage("Some message")
                val transport = it.getBean(ITransport::class.java)
                verify(transport).send(
                    checkEvent { event ->
                        assertThat(event.sdk).isNotNull
                        val sdk = event.sdk!!
                        assertThat(sdk.version).isEqualTo(BuildConfig.VERSION_NAME)
                        assertThat(sdk.name).isEqualTo(BuildConfig.SENTRY_SPRING_BOOT_SDK_NAME)
                        assertThat(sdk.packageSet).anyMatch { pkg ->
                            pkg.name == "maven:io.sentry:sentry-spring-boot-starter" && pkg.version == BuildConfig.VERSION_NAME
                        }
                        assertTrue(sdk.integrationSet.contains("SpringBoot"))
                    },
                    anyOrNull()
                )
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
    fun `registers beforeSendTransactionCallback on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomBeforeSendTransactionCallbackConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).beforeSendTransaction).isInstanceOf(CustomBeforeSendTransactionCallback::class.java)
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
                verify(transport).send(
                    checkEvent { event ->
                        assertThat(event.release).isEqualTo("git-commit-id")
                    },
                    anyOrNull()
                )
            }
    }

    @Test
    fun `sets custom release on SentryEvents if release property is set and Git integration is configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.release=my-release")
            .withUserConfiguration(MockTransportConfiguration::class.java, MockGitPropertiesConfiguration::class.java)
            .run {
                Sentry.captureMessage("Some message")
                val transport = it.getBean(ITransport::class.java)

                verify(transport).send(
                    checkEvent { event ->
                        assertThat(event.release).isEqualTo("my-release")
                    },
                    anyOrNull()
                )
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
                val userProviders = it.getSentryUserProviders()
                assertEquals(3, userProviders.size)
                assertTrue(userProviders[0] is HttpServletRequestSentryUserProvider)
                assertTrue(userProviders[1] is SpringSecuritySentryUserProvider)
                assertTrue(userProviders[2] is CustomSentryUserProvider)
            }
    }

    @Test
    fun `when custom SentryUserProvider bean with higher order is configured, it's added before HttpServletRequestSentryUserProvider`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.send-default-pii=true")
            .withConfiguration(UserConfigurations.of(SentryHighestOrderUserProviderConfiguration::class.java))
            .run {
                val userProviders = it.getSentryUserProviders()
                assertEquals(3, userProviders.size)
                assertTrue(userProviders[0] is CustomSentryUserProvider)
                assertTrue(userProviders[1] is HttpServletRequestSentryUserProvider)
                assertTrue(userProviders[2] is SpringSecuritySentryUserProvider)
            }
    }

    @Test
    fun `when Spring Security is not on the classpath, SpringSecuritySentryUserProvider is not configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.send-default-pii=true")
            .withClassLoader(FilteredClassLoader(SecurityContextHolder::class.java))
            .run { ctx ->
                val userProviders = ctx.getSentryUserProviders()
                assertTrue(userProviders.isNotEmpty())
                userProviders.forEach {
                    assertFalse(it is SpringSecuritySentryUserProvider)
                }
            }
    }

    @Test
    fun `when Spring MVC is not on the classpath, SentryExceptionResolver is not configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.send-default-pii=true")
            .withClassLoader(FilteredClassLoader(HandlerExceptionResolver::class.java))
            .run {
                assertThat(it).doesNotHaveBean(SentryExceptionResolver::class.java)
            }
    }

    @Test
    fun `when Spring MVC is not on the classpath, fallback TransactionNameProvider is configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.send-default-pii=true")
            .withClassLoader(FilteredClassLoader(HandlerExceptionResolver::class.java))
            .run {
                assertThat(it.getBean(TransactionNameProvider::class.java)).isInstanceOf(
                    SpringServletTransactionNameProvider::class.java
                )
            }
    }

    @Test
    fun `when tracing is enabled, creates tracing filter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .run {
                assertThat(it).hasBean("sentryTracingFilter")
            }
    }

    @Test
    fun `when traces sample rate is set, creates tracing filter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=0.2")
            .run {
                assertThat(it).hasBean("sentryTracingFilter")
            }
    }

    @Test
    fun `when traces sample rate is set to 0, creates tracing filter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=0.0")
            .run {
                assertThat(it).hasBean("sentryTracingFilter")
            }
    }

    @Test
    fun `when custom traces sampler callback is registered, creates tracing filter`() {
        contextRunner.withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
            .withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasBean("sentryTracingFilter")
            }
    }

    @Test
    fun `creates tracing filter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasBean("sentryTracingFilter")
            }
    }

    @Test
    fun `when tracing is enabled and sentryTracingFilter already exists, does not create tracing filter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .withUserConfiguration(CustomSentryTracingFilter::class.java)
            .run {
                assertThat(it).hasBean("sentryTracingFilter")
                val filter = it.getBean("sentryTracingFilter")

                if (filter is FilterRegistrationBean<*>) {
                    assertThat(filter.filter).isNotInstanceOf(SentryTracingFilter::class.java)
                } else {
                    assertThat(filter).isNotInstanceOf(SentryTracingFilter::class.java)
                }
            }
    }

    @Test
    fun `creates AOP beans to support @SentryCaptureExceptionParameter`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSentryExceptionParameterAdviceBeans()
            }
    }

    @Test
    fun `does not create AOP beans to support @SentryCaptureExceptionParameter if AOP class is missing`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(ProceedingJoinPoint::class.java))
            .run {
                assertThat(it).doesNotHaveSentryExceptionParameterAdviceBeans()
            }
    }

    @Test
    fun `when tracing is enabled creates AOP beans to support @SentryTransaction`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .run {
                assertThat(it).hasSentryTransactionBeans()
            }
    }

    @Test
    fun `when traces sample rate is set to 0, creates AOP beans to support @SentryTransaction`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=0.0")
            .run {
                assertThat(it).hasSentryTransactionBeans()
            }
    }

    @Test
    fun `when custom traces sampler callback is registered, creates AOP beans to support @SentryTransaction`() {
        contextRunner.withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
            .withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSentryTransactionBeans()
            }
    }

    @Test
    fun `when tracing is disabled, does not create AOP beans to support @SentryTransaction`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).doesNotHaveSentryTransactionBeans()
            }
    }

    @Test
    fun `when Spring AOP is not on the classpath, does not create AOP beans to support @SentryTransaction`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .withClassLoader(FilteredClassLoader(ProceedingJoinPoint::class.java))
            .run {
                assertThat(it).doesNotHaveSentryTransactionBeans()
            }
    }

    @Test
    fun `when tracing is enabled and custom sentryTransactionPointcut is provided, sentryTransactionPointcut bean is not created`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .withUserConfiguration(CustomSentryPerformancePointcutConfiguration::class.java)
            .run {
                assertThat(it).hasBean("sentryTransactionPointcut")
                val pointcut = it.getBean("sentryTransactionPointcut")
                assertThat(pointcut).isInstanceOf(NameMatchMethodPointcut::class.java)
            }
    }

    @Test
    fun `when tracing is enabled creates AOP beans to support @SentrySpan`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .run {
                assertThat(it).hasSentrySpanBeans()
            }
    }

    @Test
    fun `when traces sample rate is set to 0, creates AOP beans to support @SentrySpan`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=0.0")
            .run {
                assertThat(it).hasSentrySpanBeans()
            }
    }

    @Test
    fun `when custom traces sampler callback is registered, creates AOP beans to support @SentrySpan`() {
        contextRunner.withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
            .withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSentrySpanBeans()
            }
    }

    @Test
    fun `when tracing is disabled, does not create AOP beans to support @Span`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).doesNotHaveSentrySpanBeans()
            }
    }

    @Test
    fun `when Spring AOP is not on the classpath, does not create AOP beans to support @SentrySpan`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .withClassLoader(FilteredClassLoader(ProceedingJoinPoint::class.java))
            .run {
                assertThat(it).doesNotHaveSentrySpanBeans()
            }
    }

    @Test
    fun `when tracing is enabled and custom sentrySpanPointcut is provided, sentrySpanPointcut bean is not created`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .withUserConfiguration(CustomSentryPerformancePointcutConfiguration::class.java)
            .run {
                assertThat(it).hasBean("sentrySpanPointcut")
                val pointcut = it.getBean("sentrySpanPointcut")
                assertThat(pointcut).isInstanceOf(NameMatchMethodPointcut::class.java)
            }
    }

    @Test
    fun `when tracing is enabled and RestTemplate is on the classpath, SentrySpanRestTemplateCustomizer bean is created`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .run {
                assertThat(it).hasSingleBean(SentrySpanRestTemplateCustomizer::class.java)
            }
    }

    @Test
    fun `when tracing is enabled and RestTemplate is not on the classpath, SentrySpanRestTemplateCustomizer bean is not created`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .withClassLoader(FilteredClassLoader(RestTemplate::class.java))
            .run {
                assertThat(it).doesNotHaveBean(SentrySpanRestTemplateCustomizer::class.java)
            }
    }

    @Test
    fun `when tracing is enabled and WebClient is on the classpath, SentrySpanWebClientCustomizer bean is created`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .run {
                assertThat(it).hasSingleBean(SentrySpanWebClientCustomizer::class.java)
            }
    }

    @Test
    fun `when tracing is enabled and WebClient is not on the classpath, SentrySpanWebClientCustomizer bean is not created`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.traces-sample-rate=1.0")
            .withClassLoader(FilteredClassLoader(WebClient::class.java))
            .run {
                assertThat(it).doesNotHaveBean(SentrySpanWebClientCustomizer::class.java)
            }
    }

    @Test
    fun `registers tracesSamplerCallback on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).tracesSampler).isInstanceOf(CustomTracesSamplerCallback::class.java)
            }
    }

    @Test
    fun `when sentry-apache-http-client-5 is on the classpath, creates apache transport factory`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it.getBean(SentryOptions::class.java).transportFactory).isInstanceOf(ApacheHttpClientTransportFactory::class.java)
            }
    }

    @Test
    fun `when sentry-apache-http-client-5 is not on the classpath, does not create apache transport factory`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(ApacheHttpClientTransportFactory::class.java))
            .run {
                assertThat(it.getBean(SentryOptions::class.java).transportFactory).isInstanceOf(AsyncHttpTransportFactory::class.java)
            }
    }

    @Test
    fun `when sentry-apache-http-client-5 is on the classpath and custom transport factory bean is set, does not create apache transport factory`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MockTransportConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).transportFactory)
                    .isNotInstanceOf(ApacheHttpClientTransportFactory::class.java)
                    .isNotInstanceOf(NoOpTransportFactory::class.java)
            }
    }

    @Test
    fun `when MDC is on the classpath, creates ContextTagsEventProcessor`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(ContextTagsEventProcessor::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.eventProcessors).anyMatch { processor -> processor.javaClass == ContextTagsEventProcessor::class.java }
            }
    }

    @Test
    fun `when MDC is not on the classpath, does not create ContextTagsEventProcessor`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(MDC::class.java))
            .run {
                assertThat(it).doesNotHaveBean(ContextTagsEventProcessor::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.eventProcessors).noneMatch { processor -> processor.javaClass == ContextTagsEventProcessor::class.java }
            }
    }

    @Test
    fun `when AgentMarker is on the classpath and auto init off, runs SentryOpenTelemetryAgentWithoutAutoInitConfiguration`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.auto-init=false")
            .run {
                assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("SpringBootOpenTelemetryAgentWithoutAutoInit"))
            }
    }

    @Test
    fun `when AgentMarker is on the classpath and auto init on, does not run SentryOpenTelemetryAgentWithoutAutoInitConfiguration`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("SpringBootOpenTelemetryAgentWithoutAutoInit"))
            }
    }

    @Test
    fun `when AgentMarker is not on the classpath and auto init off, does not run SentryOpenTelemetryAgentWithoutAutoInitConfiguration`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.auto-init=false")
            .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
            .run {
                assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("SpringBootOpenTelemetryAgentWithoutAutoInit"))
            }
    }

    @Test
    fun `when AgentMarker is not on the classpath but OpenTelemetry is, runs SpringBootOpenTelemetryNoAgent`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
            .withUserConfiguration(OtelBeanConfig::class.java)
            .run {
                assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("SpringBootOpenTelemetryNoAgent"))
            }
    }

    @Test
    fun `when AgentMarker and OpenTelemetry are not on the classpath, does not run SpringBootOpenTelemetryNoAgent`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(AgentMarker::class.java, OpenTelemetry::class.java))
            .run {
                assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("SpringBootOpenTelemetryNoAgent"))
            }
    }

    @Test
    fun `when AgentMarker and SentryAutoConfigurationCustomizerProvider are not on the classpath, does not run SpringBootOpenTelemetryNoAgent`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(AgentMarker::class.java, SentryAutoConfigurationCustomizerProvider::class.java))
            .withUserConfiguration(OtelBeanConfig::class.java)
            .run {
                assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("SpringBootOpenTelemetryNoAgent"))
            }
    }

    @Test
    fun `when AgentMarker is not on the classpath and auto init on, does not run SentryOpenTelemetryAgentWithoutAutoInitConfiguration`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
            .run {
                assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("SpringBootOpenTelemetryAgentWithoutAutoInit"))
            }
    }

    @Test
    fun `creates quartz config`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(SchedulerFactoryBeanCustomizer::class.java)
            }
    }

    @Test
    fun `does not create quartz config if quartz lib missing`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(QuartzScheduler::class.java))
            .run {
                assertThat(it).doesNotHaveBean(SchedulerFactoryBeanCustomizer::class.java)
            }
    }

    @Test
    fun `does not create quartz config if spring-quartz lib missing`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(SchedulerFactoryBean::class.java))
            .run {
                assertThat(it).doesNotHaveBean(SchedulerFactoryBeanCustomizer::class.java)
            }
    }

    @Test
    fun `does not create quartz config if sentry-quartz lib missing`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(SentryJobListener::class.java))
            .run {
                assertThat(it).doesNotHaveBean(SchedulerFactoryBeanCustomizer::class.java)
            }
    }

    @Test
    fun `Sentry quartz job listener is added`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-automatic-checkins=true")
            .withUserConfiguration(QuartzAutoConfiguration::class.java)
            .run {
                val jobListeners = it.getBean(Scheduler::class.java).listenerManager.jobListeners
                assertThat(jobListeners).hasSize(1)
                assertThat(jobListeners[0]).matches(
                    { it.name == "sentry-job-listener" },
                    "is sentry job listener"
                )
            }
    }

    @Test
    fun `user defined SchedulerFactoryBeanCustomizer overrides Sentry Customizer`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-automatic-checkins=true")
            .withUserConfiguration(QuartzAutoConfiguration::class.java, CustomSchedulerFactoryBeanCustomizerConfiguration::class.java)
            .run {
                val jobListeners = it.getBean(Scheduler::class.java).listenerManager.jobListeners
                assertThat(jobListeners).hasSize(1)
                assertThat(jobListeners[0]).matches(
                    { it.name == "custom-job-listener" },
                    "is custom job listener"
                )
            }
    }

    @Test
    fun `registers SpringProfilesEventProcessor on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it.getBean(SentryOptions::class.java).eventProcessors).anyMatch { processor -> processor.javaClass == SpringProfilesEventProcessor::class.java }
            }
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomSchedulerFactoryBeanCustomizerConfiguration {
        class MyJobListener : JobListener {
            override fun getName() = "custom-job-listener"

            override fun jobToBeExecuted(context: JobExecutionContext?) {
                // do nothing
            }

            override fun jobExecutionVetoed(context: JobExecutionContext?) {
                // do nothing
            }

            override fun jobWasExecuted(
                context: JobExecutionContext?,
                jobException: JobExecutionException?
            ) {
                // do nothing
            }
        }

        @Bean
        open fun mySchedulerFactoryBeanCustomizer(): SchedulerFactoryBeanCustomizer {
            return SchedulerFactoryBeanCustomizer { schedulerFactoryBean -> schedulerFactoryBean.setGlobalJobListeners(MyJobListener()) }
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomOptionsConfigurationConfiguration {

        @Bean
        open fun customOptionsConfiguration() = Sentry.OptionsConfiguration<SentryOptions> {
            it.setBeforeSend(null)
        }

        @Bean
        open fun beforeSendCallback() = CustomBeforeSendCallback()
    }

    @Configuration(proxyBeanMethods = false)
    open class OverridingOptionsConfigurationConfiguration {

        @Bean
        open fun sentryOptionsConfiguration() = Sentry.OptionsConfiguration<SentryOptions> {
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

    @Configuration(proxyBeanMethods = false)
    open class CustomBeforeSendCallbackConfiguration {

        @Bean
        open fun beforeSendCallback() = CustomBeforeSendCallback()
    }

    class CustomBeforeSendCallback : SentryOptions.BeforeSendCallback {
        override fun execute(event: SentryEvent, hint: Hint): SentryEvent? = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomBeforeSendTransactionCallbackConfiguration {

        @Bean
        open fun beforeSendTransactionCallback() = CustomBeforeSendTransactionCallback()
    }

    class CustomBeforeSendTransactionCallback : SentryOptions.BeforeSendTransactionCallback {
        override fun execute(event: SentryTransaction, hint: Hint): SentryTransaction? = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomBeforeBreadcrumbCallbackConfiguration {

        @Bean
        open fun beforeBreadcrumbCallback() = CustomBeforeBreadcrumbCallback()
    }

    class CustomBeforeBreadcrumbCallback : SentryOptions.BeforeBreadcrumbCallback {
        override fun execute(breadcrumb: Breadcrumb, hint: Hint): Breadcrumb? = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomEventProcessorConfiguration {

        @Bean
        open fun customEventProcessor() = CustomEventProcessor()
    }

    class CustomEventProcessor : EventProcessor {
        override fun process(event: SentryEvent, hint: Hint) = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomIntegrationConfiguration {

        @Bean
        open fun customIntegration() = CustomIntegration()
    }

    class CustomIntegration : Integration {
        override fun register(scopes: IScopes, options: SentryOptions) {}
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

    @Configuration
    open class CustomSentryTracingFilter {

        @Bean
        open fun sentryTracingFilter() = mock<Filter>()
    }

    @Configuration
    open class CustomSentryPerformancePointcutConfiguration {

        @Bean
        open fun sentryTransactionPointcut() = NameMatchMethodPointcut()

        @Bean
        open fun sentrySpanPointcut() = NameMatchMethodPointcut()
    }

    @Configuration
    open class CustomTracesSamplerCallbackConfiguration {

        @Bean
        open fun tracingSamplerCallback() = CustomTracesSamplerCallback()
    }

    class CustomTracesSamplerCallback : SentryOptions.TracesSamplerCallback {
        override fun sample(samplingContext: SamplingContext) = 1.0
    }

    /**
     * this should be taken care of by the otel spring starter in a real application
     */
    @Configuration
    open class OtelBeanConfig {

        @Bean
        open fun openTelemetry() = OpenTelemetry.noop()
    }

    open class CustomSentryUserProvider : SentryUserProvider {
        override fun provideUser(): User? {
            val user = User()
            user.username = "john.smith"
            return user
        }
    }

    private fun <C : ApplicationContext> ApplicationContextAssert<C>.hasSentryTransactionBeans(): ApplicationContextAssert<C> {
        this.hasBean("sentryTransactionPointcut")
        this.hasBean("sentryTransactionAdvice")
        this.hasBean("sentryTransactionAdvisor")
        return this
    }

    private fun <C : ApplicationContext> ApplicationContextAssert<C>.doesNotHaveSentryTransactionBeans(): ApplicationContextAssert<C> {
        this.doesNotHaveBean("sentryTransactionPointcut")
        this.doesNotHaveBean("sentryTransactionAdvice")
        this.doesNotHaveBean("sentryTransactionAdvisor")
        return this
    }

    private fun <C : ApplicationContext> ApplicationContextAssert<C>.hasSentrySpanBeans(): ApplicationContextAssert<C> {
        this.hasBean("sentrySpanPointcut")
        this.hasBean("sentrySpanAdvice")
        this.hasBean("sentrySpanAdvisor")
        return this
    }

    private fun <C : ApplicationContext> ApplicationContextAssert<C>.doesNotHaveSentrySpanBeans(): ApplicationContextAssert<C> {
        this.doesNotHaveBean("sentrySpanPointcut")
        this.doesNotHaveBean("sentrySpanAdvice")
        this.doesNotHaveBean("sentrySpanAdvisor")
        return this
    }

    private fun <C : ApplicationContext> ApplicationContextAssert<C>.hasSentryExceptionParameterAdviceBeans(): ApplicationContextAssert<C> {
        this.hasBean("sentryCaptureExceptionParameterPointcut")
        this.hasBean("sentryCaptureExceptionParameterAdvice")
        this.hasBean("sentryCaptureExceptionParameterAdvisor")
        return this
    }

    private fun <C : ApplicationContext> ApplicationContextAssert<C>.doesNotHaveSentryExceptionParameterAdviceBeans(): ApplicationContextAssert<C> {
        this.doesNotHaveBean("sentryCaptureExceptionParameterPointcut")
        this.doesNotHaveBean("sentryCaptureExceptionParameterAdvice")
        this.doesNotHaveBean("sentryCaptureExceptionParameterAdvisor")
        return this
    }

    private fun ApplicationContext.getSentryUserProviders(): List<SentryUserProvider> {
        val userFilter = this.getBean("sentryUserFilter", FilterRegistrationBean::class.java).filter as SentryUserFilter
        return userFilter.sentryUserProviders
    }
}
