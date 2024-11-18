package io.sentry.kotlin

import io.sentry.ScopeType
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryContextTest {

    // TODO [HSM] In global hub mode SentryContext behaves differently
    // because Sentry.getCurrentScopes always returns rootScopes
    // What's the desired behaviour?

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
            assertNotNull(getTag("c1"))
        }
        listOf(c1, c2).joinAll()
        assertNotNull(getTag("parent"))
        assertNotNull(getTag("c1"))
        assertNotNull(getTag("c2"))
        return@runBlocking
    }

    @Test
    fun testContextIsNotPassedByDefaultBetweenCoroutinesCurrentScope() = runBlocking {
        Sentry.configureScope(ScopeType.CURRENT) { scope ->
            scope.setTag("parent", "parentValue")
        }
        val c1 = launch(SentryContext()) {
            Sentry.configureScope(ScopeType.CURRENT) { scope ->
                scope.setTag("c1", "c1value")
            }
            assertEquals("c1value", getTag("c1", ScopeType.CURRENT))
            assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
            assertNull(getTag("c2", ScopeType.CURRENT))
        }
        val c2 = launch(SentryContext()) {
            Sentry.configureScope(ScopeType.CURRENT) { scope ->
                scope.setTag("c2", "c2value")
            }
            assertEquals("c2value", getTag("c2", ScopeType.CURRENT))
            assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
            assertNull(getTag("c1", ScopeType.CURRENT))
        }
        listOf(c1, c2).joinAll()
        assertNotNull(getTag("parent", ScopeType.CURRENT))
        assertNull(getTag("c1", ScopeType.CURRENT))
        assertNull(getTag("c2", ScopeType.CURRENT))
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
    fun testContextIsClonedWhenPassedToChildCurrentScope() = runBlocking {
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
            assertNotNull(getTag("c2"))
        }.join()
        assertNotNull(getTag("parent"))
        assertNotNull(getTag("c1"))
        assertNotNull(getTag("c2"))
        return@runBlocking
    }

    @Test
    fun testContextIsClonedWhenPassedToChild() = runBlocking {
        Sentry.configureScope(ScopeType.CURRENT) { scope ->
            scope.setTag("parent", "parentValue")
        }
        launch(SentryContext()) {
            Sentry.configureScope(ScopeType.CURRENT) { scope ->
                scope.setTag("c1", "c1value")
            }
            assertEquals("c1value", getTag("c1", ScopeType.CURRENT))
            assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
            assertNull(getTag("c2", ScopeType.CURRENT))

            val c2 = launch() {
                Sentry.configureScope(ScopeType.CURRENT) { scope ->
                    scope.setTag("c2", "c2value")
                }
                assertEquals("c2value", getTag("c2", ScopeType.CURRENT))
                assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
                assertNotNull(getTag("c1", ScopeType.CURRENT))
            }

            c2.join()

            assertNotNull(getTag("c1", ScopeType.CURRENT))
            assertNull(getTag("c2", ScopeType.CURRENT))
        }.join()
        assertNotNull(getTag("parent", ScopeType.CURRENT))
        assertNull(getTag("c1", ScopeType.CURRENT))
        assertNull(getTag("c2", ScopeType.CURRENT))
    }

    @Test
    fun testExplicitlyPassedContextOverridesPropagatedContext() = runBlocking {
        Sentry.setTag("parent", "parentValue")
        launch(SentryContext()) {
            Sentry.setTag("c1", "c1value")
            assertEquals("c1value", getTag("c1"))
            assertEquals("parentValue", getTag("parent"))
            assertNull(getTag("c2"))

            val c2 = launch(
                SentryContext(
                    Sentry.getCurrentScopes().forkedScopes("test").also {
                        it.setTag("cloned", "clonedValue")
                    }
                )
            ) {
                Sentry.setTag("c2", "c2value")
                assertEquals("c2value", getTag("c2"))
                assertEquals("parentValue", getTag("parent"))
                assertNotNull(getTag("c1"))
                assertNotNull(getTag("cloned"))
            }

            c2.join()

            assertNotNull(getTag("c1"))
            assertNull(getTag("c2"))
            assertNull(getTag("cloned"))
        }.join()

        assertNotNull(getTag("c1"))
        assertNull(getTag("c2"))
        assertNull(getTag("cloned"))
        return@runBlocking
    }

    @Test
    fun testExplicitlyPassedContextOverridesPropagatedContextCurrentScope() = runBlocking {
        Sentry.configureScope(ScopeType.CURRENT) { scope ->
            scope.setTag("parent", "parentValue")
        }
        launch(SentryContext()) {
            Sentry.configureScope(ScopeType.CURRENT) { scope ->
                scope.setTag("c1", "c1value")
            }
            assertEquals("c1value", getTag("c1", ScopeType.CURRENT))
            assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
            assertNull(getTag("c2", ScopeType.CURRENT))

            val c2 = launch(
                SentryContext(
                    Sentry.getCurrentScopes().forkedCurrentScope("test").also {
                        it.configureScope(ScopeType.CURRENT) { scope ->
                            scope.setTag("cloned", "clonedValue")
                        }
                    }
                )
            ) {
                Sentry.configureScope(ScopeType.CURRENT) { scope ->
                    scope.setTag("c2", "c2value")
                }
                assertEquals("c2value", getTag("c2", ScopeType.CURRENT))
                assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
                assertNotNull(getTag("c1", ScopeType.CURRENT))
                assertNotNull(getTag("cloned", ScopeType.CURRENT))
            }

            c2.join()

            assertNotNull(getTag("c1", ScopeType.CURRENT))
            assertNull(getTag("c2", ScopeType.CURRENT))
            assertNull(getTag("cloned", ScopeType.CURRENT))
        }
        assertNull(getTag("c1", ScopeType.CURRENT))
        assertNull(getTag("c2", ScopeType.CURRENT))
        assertNull(getTag("cloned", ScopeType.CURRENT))
    }

    @Test
    fun `mergeForChild returns copy of initial context if Key not present`() {
        val initialContextElement = SentryContext(
            Sentry.getCurrentScopes().forkedScopes("test").also {
                it.setTag("cloned", "clonedValue")
            }
        )
        val mergedContextElement = initialContextElement.mergeForChild(CoroutineName("test"))

        assertNotEquals(initialContextElement, mergedContextElement)
        assertNotNull((mergedContextElement)[initialContextElement.key])
    }

    @Test
    fun `mergeForChild returns passed context`() {
        val initialContextElement = SentryContext(
            Sentry.getCurrentScopes().forkedScopes("test").also {
                it.setTag("cloned", "clonedValue")
            }
        )
        val mergedContextElement = SentryContext().mergeForChild(initialContextElement)

        assertEquals(initialContextElement, mergedContextElement)
    }

    private fun getTag(tag: String, scopeType: ScopeType = ScopeType.ISOLATION): String? {
        var value: String? = null
        Sentry.configureScope(scopeType) {
            value = it.tags[tag]
        }
        return value
    }
}
