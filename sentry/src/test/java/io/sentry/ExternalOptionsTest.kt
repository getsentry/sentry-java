package io.sentry

import io.sentry.config.PropertiesProviderFactory
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalOptionsTest {
    @Test
    fun `creates options with proxy using external properties`() {
        withPropertiesFile(listOf("proxy.host=proxy.example.com", "proxy.port=9090", "proxy.user=some-user", "proxy.pass=some-pass")) {
            assertNotNull(it.proxy) { proxy ->
                assertEquals("proxy.example.com", proxy.host)
                assertEquals("9090", proxy.port)
                assertEquals("some-user", proxy.user)
                assertEquals("some-pass", proxy.pass)
            }
        }
    }

    @Test
    fun `when proxy port is not set default proxy port is used`() {
        withPropertiesFile("proxy.host=proxy.example.com") {
            assertNotNull(it.proxy)
            assertEquals("proxy.example.com", it.proxy!!.host)
            assertEquals("80", it.proxy!!.port)
        }
    }

    @Test
    fun `creates options with tags using external properties`() {
        withPropertiesFile(listOf("tags.tag1=value1", "tags.tag2=value2")) {
            assertEquals(mapOf("tag1" to "value1", "tag2" to "value2"), it.tags)
        }
    }

    @Test
    fun `creates options with uncaught handler set to true enabled using external properties`() {
        withPropertiesFile("uncaught.handler.enabled=true") { options ->
            assertNotNull(options.enableUncaughtExceptionHandler) {
                assertTrue(it)
            }
        }
    }

    @Test
    fun `creates options with uncaught handler set to false enabled using external properties`() {
        withPropertiesFile("uncaught.handler.enabled=false") { options ->
            assertNotNull(options.enableUncaughtExceptionHandler) {
                assertFalse(it)
            }
        }
    }

    @Test
    fun `creates options with uncaught handler set to null enabled using external properties`() {
        withPropertiesFile {
            assertNull(it.enableUncaughtExceptionHandler)
        }
    }

    @Test
    fun `creates options with debug set to true enabled using external properties`() {
        withPropertiesFile("debug=true") {
            assertNotNull(it.debug) {
                assertTrue(it)
            }
        }
    }

    @Test
    fun `creates options with debug set to false enabled using external properties`() {
        withPropertiesFile("debug=false") {
            assertNotNull(it.debug) {
                assertFalse(it)
            }
        }
    }

    @Test
    fun `creates options with debug set to null enabled using external properties`() {
        withPropertiesFile {
            val mergeResult =
                SentryOptions().apply {
                    setDebug(true)
                }
            mergeResult.merge(it)
            assertTrue(mergeResult.isDebug)
        }
    }

    @Test
    fun `creates options with inAppInclude and inAppExclude using external properties`() {
        withPropertiesFile(listOf("in-app-includes=org.springframework,com.myapp", "in-app-excludes=org.jboss,com.microsoft")) {
            assertEquals(listOf("org.springframework", "com.myapp"), it.inAppIncludes)
            assertEquals(listOf("org.jboss", "com.microsoft"), it.inAppExcludes)
        }
    }

    @Test
    fun `creates options with tracesSampleRate using external properties`() {
        withPropertiesFile("traces-sample-rate=0.2") {
            assertEquals(0.2, it.tracesSampleRate)
        }
    }

    @Test
    fun `creates options with profilesSampleRate using external properties`() {
        withPropertiesFile("profiles-sample-rate=0.2") {
            assertEquals(0.2, it.profilesSampleRate)
        }
    }

    @Test
    fun `creates options with enableDeduplication using external properties`() {
        withPropertiesFile("enable-deduplication=true") {
            assertNotNull(it.enableDeduplication) {
                assertTrue(it)
            }
        }
    }

    @Test
    fun `creates options with sendClientReports using external properties`() {
        withPropertiesFile("send-client-reports=false") {
            assertNotNull(it.sendClientReports) {
                assertFalse(it)
            }
        }
    }

    @Test
    fun `creates options with maxRequestBodySize using external properties`() {
        withPropertiesFile("max-request-body-size=small") {
            assertEquals(SentryOptions.RequestSize.SMALL, it.maxRequestBodySize)
        }
    }

    @Test
    fun `creates options with trace propagation targets using external properties`() {
        withPropertiesFile("""trace-propagation-targets=localhost,^(http|https)://api\\..*$""") {
            assertEquals(listOf("localhost", """^(http|https)://api\..*$"""), it.tracePropagationTargets)
        }
    }

    @Test
    fun `creates options without trace propagation targets results in default tracePropagationTargets being null`() {
        withPropertiesFile("""""") {
            assertEquals(null, it.tracePropagationTargets)
        }
    }

    @Test
    fun `creates options with empty trace propagation targets, results in empty list`() {
        withPropertiesFile("""trace-propagation-targets=""") {
            assertTrue(it.tracePropagationTargets?.isEmpty() == true)
        }
    }

    @Test
    fun `creates options with tracingOrigins using external properties`() {
        withPropertiesFile("""tracing-origins=localhost,^(http|https)://api\\..*$""") {
            assertEquals(listOf("localhost", """^(http|https)://api\..*$"""), it.tracePropagationTargets)
        }
    }

    @Test
    fun `creates options with context tags using external properties`() {
        withPropertiesFile("context-tags=userId,xxx") {
            assertEquals(listOf("userId", "xxx"), it.contextTags)
        }
    }

    @Test
    fun `creates options with proguardUuid using external properties`() {
        withPropertiesFile("proguard-uuid=id") {
            assertEquals("id", it.proguardUuid)
        }
    }

    @Test
    fun `creates options with idleTimeout using external properties`() {
        withPropertiesFile("idle-timeout=2000") {
            assertEquals(2000L, it.idleTimeout)
        }
    }

    @Test
    fun `creates options with ignored exception types using external properties`() {
        val logger = mock<ILogger>()
        // Setting few types of classes:
        // - RuntimeException and IllegalStateException - valid exception classes
        // - NonExistingClass - class that does not exist - should not trigger a failure to create options
        // - io.sentry.Sentry - class that does not extend Throwable - should not trigger a failure
        withPropertiesFile(
            "ignored-exceptions-for-type=java.lang.RuntimeException,java.lang.IllegalStateException,com.xx.NonExistingClass,io.sentry.Sentry",
            logger,
        ) { options ->
            assertTrue(options.ignoredExceptionsForType.contains(RuntimeException::class.java))
            assertTrue(options.ignoredExceptionsForType.contains(IllegalStateException::class.java))
            verify(logger).log(eq(SentryLevel.WARNING), any<String>(), eq("com.xx.NonExistingClass"), eq("com.xx.NonExistingClass"))
            verify(logger).log(eq(SentryLevel.WARNING), any<String>(), eq("io.sentry.Sentry"), eq("io.sentry.Sentry"))
        }
    }

    @Test
    fun `creates options with ignored error patterns using external properties`() {
        val logger = mock<ILogger>()
        withPropertiesFile("ignored-errors=Some error,Another .*", logger) { options ->
            assertTrue(options.ignoredErrors!!.contains("Some error"))
            assertTrue(options.ignoredErrors!!.contains("Another .*"))
        }
    }

    @Test
    fun `creates options with null ignored errors if missing`() {
        val logger = mock<ILogger>()
        withPropertiesFile("Another .*", logger) { options ->
            assertNull(options.ignoredErrors)
        }
    }

    @Test
    fun `creates options with single bundle ID using external properties`() {
        withPropertiesFile("bundle-ids=12ea7a02-46ac-44c0-a5bb-6d1fd9586411") { options ->
            assertTrue(options.bundleIds.containsAll(listOf("12ea7a02-46ac-44c0-a5bb-6d1fd9586411")))
        }
    }

    @Test
    fun `creates options with multiple bundle IDs using external properties`() {
        withPropertiesFile("bundle-ids=12ea7a02-46ac-44c0-a5bb-6d1fd9586411,faa3ab42-b1bd-4659-af8e-1682324aa744") { options ->
            assertTrue(
                options.bundleIds.containsAll(listOf("12ea7a02-46ac-44c0-a5bb-6d1fd9586411", "faa3ab42-b1bd-4659-af8e-1682324aa744")),
            )
        }
    }

    @Test
    fun `creates options with empty bundle IDs using external properties`() {
        withPropertiesFile("bundle-ids=") { options ->
            assertTrue(options.bundleIds.size == 1)
            // trimming is tested in SentryOptionsTest so even though there's an empty string here
            // it will be filtered when being merged with SentryOptions
            assertTrue(options.bundleIds.containsAll(listOf("")))
        }
    }

    @Test
    fun `creates options with missing bundle IDs using external properties`() {
        withPropertiesFile("") { options ->
            assertTrue(options.bundleIds.isEmpty())
        }
    }

    @Test
    fun `creates options with enabled set to true`() {
        withPropertiesFile("enabled=true") { options ->
            assertTrue(options.isEnabled() == true)
        }
    }

    @Test
    fun `creates options with sendModules set to false`() {
        withPropertiesFile("send-modules=false") { options ->
            assertTrue(options.isSendModules == false)
        }
    }

    @Test
    fun `creates options with ignoredCheckIns`() {
        withPropertiesFile("ignored-checkins=slugA,slug2") { options ->
            assertTrue(options.ignoredCheckIns!!.containsAll(listOf("slugA", "slug2")))
        }
    }

    @Test
    fun `creates options with null ignoredCheckIns if missing`() {
        val logger = mock<ILogger>()
        withPropertiesFile("Another .*", logger) { options ->
            assertNull(options.ignoredCheckIns)
        }
    }

    @Test
    fun `creates options with ignoredTransactions`() {
        withPropertiesFile("ignored-transactions=transactionName1,transactionName2") { options ->
            assertTrue(options.ignoredTransactions!!.containsAll(listOf("transactionName1", "transactionName2")))
        }
    }

    @Test
    fun `creates options with null ignoredTransactions if missing`() {
        val logger = mock<ILogger>()
        withPropertiesFile("Another .*", logger) { options ->
            assertNull(options.ignoredTransactions)
        }
    }

    @Test
    fun `creates options with enableBackpressureHandling set to false`() {
        withPropertiesFile("enable-backpressure-handling=false") { options ->
            assertTrue(options.isEnableBackpressureHandling == false)
        }
    }

    @Test
    fun `creates options with cron defaults`() {
        withPropertiesFile(
            listOf(
                "cron.default-checkin-margin=1",
                "cron.default-max-runtime=30",
                "cron.default-timezone=America/New_York",
                "cron.default-failure-issue-threshold=40",
                "cron.default-recovery-threshold=50",
            ),
        ) { options ->
            assertEquals(1L, options.cron?.defaultCheckinMargin)
            assertEquals(30L, options.cron?.defaultMaxRuntime)
            assertEquals("America/New_York", options.cron?.defaultTimezone)
            assertEquals(40L, options.cron?.defaultFailureIssueThreshold)
            assertEquals(50L, options.cron?.defaultRecoveryThreshold)
        }
    }

    @Test
    fun `creates options with sendDefaultPii set to true`() {
        withPropertiesFile("send-default-pii=true") { options ->
            assertTrue(options.isSendDefaultPii == true)
        }
    }

    @Test
    fun `creates options with forceInit set to true`() {
        withPropertiesFile("force-init=true") { options ->
            assertTrue(options.isForceInit == true)
        }
    }

    @Test
    fun `creates options with enableSpotlight set to true`() {
        withPropertiesFile("enable-spotlight=true") { options ->
            assertTrue(options.isEnableSpotlight == true)
        }
    }

    @Test
    fun `creates options with spotlightConnectionUrl set`() {
        withPropertiesFile("spotlight-connection-url=http://local.sentry.io:1234") { options ->
            assertEquals("http://local.sentry.io:1234", options.spotlightConnectionUrl)
        }
    }

    @Test
    fun `creates options with globalHubMode set to true`() {
        withPropertiesFile("global-hub-mode=true") { options ->
            assertTrue(options.isGlobalHubMode == true)
        }
    }

    @Test
    fun `creates options with globalHubMode set to false`() {
        withPropertiesFile("global-hub-mode=false") { options ->
            assertTrue(options.isGlobalHubMode == false)
        }
    }

    @Test
    fun `creates options with captureOpenTelemetryEvents set to false`() {
        withPropertiesFile("capture-open-telemetry-events=false") { options ->
            assertTrue(options.isCaptureOpenTelemetryEvents == false)
        }
    }

    @Test
    fun `creates options with captureOpenTelemetryEvents set to true`() {
        withPropertiesFile("capture-open-telemetry-events=true") { options ->
            assertTrue(options.isCaptureOpenTelemetryEvents == true)
        }
    }

    @Test
    fun `creates options with enableLogs set to true`() {
        withPropertiesFile("logs.enabled=true") { options ->
            assertTrue(options.isEnableLogs == true)
        }
    }

    private fun withPropertiesFile(
        textLines: List<String> = emptyList(),
        logger: ILogger = mock(),
        fn: (ExternalOptions) -> Unit,
    ) {
        // create a sentry.properties file in temporary folder
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        val file = temporaryFolder.newFile("sentry.properties")
        textLines.forEach { file.appendText("$it\n") }
        // set location of the sentry.properties file
        System.setProperty("sentry.properties.file", file.absolutePath)

        try {
            val options = ExternalOptions.from(PropertiesProviderFactory.create(), logger)
            fn.invoke(options)
        } finally {
            temporaryFolder.delete()
        }
    }

    private fun withPropertiesFile(
        text: String,
        logger: ILogger = mock(),
        fn: (ExternalOptions) -> Unit,
    ) {
        withPropertiesFile(listOf(text), logger, fn)
    }
}
