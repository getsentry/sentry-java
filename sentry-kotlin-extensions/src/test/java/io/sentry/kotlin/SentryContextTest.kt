package io.sentry.kotlin

import io.sentry.Sentry
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        Sentry.setTag("parent", "parentValue")
        val c1 = launch(SentryContext()) {
            Sentry.setTag("c1", "c1value")
            assertEquals("c1value", getTag("c1"))
            assertEquals("parentValue", getTag("parent"))
            assertNull(getTag("c2"))
        }
        val c2 = launch(SentryContext()) {
            Sentry.setTag("c2", "c2value")
            assertEquals("c2value", getTag("c2"))
            assertEquals("parentValue", getTag("parent"))
            assertNull(getTag("c1"))
        }
        listOf(c1, c2).joinAll()
        assertNull(getTag("c1"))
        assertNull(getTag("c2"))
    }

    @Test
    fun testContextIsPassedToChildCoroutines() = runBlocking {
        val c1 = launch(SentryContext()) {
            Sentry.setTag("c1", "c1value")
            assertEquals("c1value", getTag("c1"))
            assertNull(getTag("c2"))
            launch() {
                Sentry.setTag("c2", "c2value")
                assertEquals("c2value", getTag("c2"))
                assertEquals("c1value", getTag("c1"))
            }.join()
        }
        c1.join()
    }

    @Test
    fun testContextPassedWhileOnMainThread() {
        Sentry.setTag("myKey", "myValue")
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
    fun testContextMayBeEmpty() {
        runBlocking(SentryContext()) {
            assertNull(getTag("myKey"))
        }
    }

    @Test
    fun testContextIsClonedWhenPassedToChild() = runBlocking {
        Sentry.setTag("parent", "parentValue")
        launch(SentryContext()) {
            Sentry.setTag("c1", "c1value")
            assertEquals("c1value", getTag("c1"))
            assertEquals("parentValue", getTag("parent"))
            assertNull(getTag("c2"))

            val c2 = launch() {
                Sentry.setTag("c2", "c2value")
                assertEquals("c2value", getTag("c2"))
                assertEquals("parentValue", getTag("parent"))
                assertNotNull(getTag("c1"))
            }

            c2.join()

            assertNotNull(getTag("c1"))
            assertNull(getTag("c2"))
        }
        assertNull(getTag("c1"))
        assertNull(getTag("c2"))
    }

    private fun getTag(tag: String): String? {
        var value: String? = null
        Sentry.configureScope {
            value = it.tags[tag]
        }
        return value
    }
}
