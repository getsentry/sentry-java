package io.sentry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.expect

class SentryContextTest {

    @BeforeTest
    fun init() {
        Sentry.init("https://key@sentry.io/123")
    }

    @AfterTest
    fun close() {
        Sentry.close()
    }

    @Test
    fun testContextIsNotPassedByDefaultBetweenCoroutines() = runBlocking {
        Sentry.setTag("myKey", "myValue")
        // Standalone launch
        GlobalScope.launch {
            assertNull(getTag("myKey"))
        }.join()
    }

    private fun getTag(tag:String): String? {
        var value: String? = null
        Sentry.configureScope {
            value = it.tags[tag]
        }
        return value
    }

    @Test
    fun testContextCanBePassedBetweenCoroutines() = runBlocking {
        Sentry.setTag("myKey", "myValue")
        // Scoped launch with MDCContext element
        launch(SentryContext()) {
            assertEquals("myValue", getTag("myKey"))
        }.join()
    }

    @Test
    fun testContextInheritanceMDC() = runBlocking {
        MDC.put("myKey", "myValue")
        withContext(MDCContext()) {
            MDC.put("myKey", "myValue2")
            // Scoped launch with inherited MDContext element
            launch(Dispatchers.Default) {
                assertEquals("myValue", MDC.get("myKey"))
            }.join()
        }
        assertEquals("myValue", MDC.get("myKey"))
    }

    @Test
    fun testContextInheritance() = runBlocking {
        Sentry.setTag("myKey", "myValue")
        withContext(SentryContext()) {
            Sentry.setTag("myKey", "myValue2")
            // Scoped launch with inherited MDContext element
            launch(Dispatchers.Default) {
                assertEquals("myValue", getTag("myKey"))
            }.join()

        }
        assertEquals("myValue", getTag("myKey"))
    }

    fun tags(): Map<String, String>? {
        var result:Map<String, String>? = null
        Sentry.configureScope {
            result= it.tags
        }
        return result
    }

    @Test
    fun testContextPassedWhileOnMainThread() {
        Sentry.setTag("myKey", "myValue")
        // No MDCContext element
        runBlocking {
            assertEquals("myValue", getTag("myKey"))
        }
    }

    @Test
    fun testContextCanBePassedWhileOnMainThread() {
        Sentry.setTag("myKey", "myValue")
        runBlocking(SentryContext()) {
            assertEquals("myValue", getTag("myKey"))
        }
    }

    @Test
    fun testContextNeededWithOtherContext() {
        Sentry.setTag("myKey", "myValue")
        runBlocking(SentryContext()) {
            assertEquals("myValue", getTag("myKey"))
        }
    }

    @Test
    fun testContextMayBeEmpty() {
        runBlocking(SentryContext()) {
            assertNull(getTag("myKey"))
        }
    }

    @Test
    fun testContextWithContext() = runBlocking {
        Sentry.setTag("myKey", "myValue")
        val mainDispatcher = kotlin.coroutines.coroutineContext[ContinuationInterceptor]!!
        withContext(Dispatchers.Default + SentryContext()) {
            assertEquals("myValue", getTag("myKey"))
            withContext(mainDispatcher) {
                assertEquals("myValue", getTag("myKey"))
            }
        }
    }
}
