package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class CombinedScopeViewTest {

    @Test
    fun `adds breadcrumbs from all scopes in sorted order`() {
        val options = SentryOptions()
        val globalScope = Scope(options)
        val isolationScope = Scope(options)
        val scope = Scope(options)

        val combined = CombinedScopeView(globalScope, isolationScope, scope)

        globalScope.addBreadcrumb(Breadcrumb.info("global 1"))
        isolationScope.addBreadcrumb(Breadcrumb.info("isolation 1"))
        scope.addBreadcrumb(Breadcrumb.info("current 1"))

        globalScope.addBreadcrumb(Breadcrumb.info("global 2"))
        isolationScope.addBreadcrumb(Breadcrumb.info("isolation 2"))
        scope.addBreadcrumb(Breadcrumb.info("current 2"))

        val breadcrumbs = combined.breadcrumbs
        assertEquals("global 1", breadcrumbs.poll().message)
        assertEquals("isolation 1", breadcrumbs.poll().message)
        assertEquals("current 1", breadcrumbs.poll().message)
        assertEquals("global 2", breadcrumbs.poll().message)
        assertEquals("isolation 2", breadcrumbs.poll().message)
        assertEquals("current 2", breadcrumbs.poll().message)
    }

    @Test
    fun `oldest breadcrumbs are dropped first`() {
        val options = SentryOptions().also { it.maxBreadcrumbs = 5 }
        val globalScope = Scope(options)
        val isolationScope = Scope(options)
        val scope = Scope(options)

        val combined = CombinedScopeView(globalScope, isolationScope, scope)

        globalScope.addBreadcrumb(Breadcrumb.info("global 1"))
        isolationScope.addBreadcrumb(Breadcrumb.info("isolation 1"))
        scope.addBreadcrumb(Breadcrumb.info("current 1"))

        globalScope.addBreadcrumb(Breadcrumb.info("global 2"))
        isolationScope.addBreadcrumb(Breadcrumb.info("isolation 2"))
        scope.addBreadcrumb(Breadcrumb.info("current 2"))

        val breadcrumbs = combined.breadcrumbs
//        assertEquals("global 1", breadcrumbs.poll().message) <-- was dropped
        assertEquals("isolation 1", breadcrumbs.poll().message)
        assertEquals("current 1", breadcrumbs.poll().message)
        assertEquals("global 2", breadcrumbs.poll().message)
        assertEquals("isolation 2", breadcrumbs.poll().message)
        assertEquals("current 2", breadcrumbs.poll().message)

        scope.addBreadcrumb(Breadcrumb.info("current 3"))
        scope.addBreadcrumb(Breadcrumb.info("current 4"))

        val breadcrumbs2 = combined.breadcrumbs
//        assertEquals("global 1", breadcrumbs.poll().message) <-- was dropped
//        assertEquals("isolation 1", breadcrumbs2.poll().message) <-- dropped
//        assertEquals("current 1", breadcrumbs2.poll().message) <-- dropped
        assertEquals("global 2", breadcrumbs2.poll().message)
        assertEquals("isolation 2", breadcrumbs2.poll().message)
        assertEquals("current 2", breadcrumbs2.poll().message)
        assertEquals("current 3", breadcrumbs2.poll().message)
        assertEquals("current 4", breadcrumbs2.poll().message)
    }

    @Test
    fun `event processors from options are not returned`() {
        val options = SentryOptions().also {
            it.addEventProcessor(MainEventProcessor(it))
        }

        val globalScope = Scope(options)
        val isolationScope = Scope(options)
        val scope = Scope(options)

        val combined = CombinedScopeView(globalScope, isolationScope, scope)

        assertEquals(0, combined.eventProcessors.size)
    }

    @Test
    fun `event processors from options and all scopes in order`() {
        val options = SentryOptions()

        val globalScope = Scope(options)
        val isolationScope = Scope(options)
        val scope = Scope(options)

        val first = TestEventProcessor(0).also { scope.addEventProcessor(it) }
        val second = TestEventProcessor(1000).also { globalScope.addEventProcessor(it) }
        val third = TestEventProcessor(2000).also { isolationScope.addEventProcessor(it) }
        val fourth = TestEventProcessor(3000).also { scope.addEventProcessor(it) }

        val combined = CombinedScopeView(globalScope, isolationScope, scope)

        val eventProcessors = combined.eventProcessors

        assertEquals(first, eventProcessors.get(0))
        assertEquals(second, eventProcessors.get(1))
        assertEquals(third, eventProcessors.get(2))
        assertEquals(fourth, eventProcessors.get(3))
    }

    class TestEventProcessor(val orderNumber: Long?) : EventProcessor {
        override fun getOrder() = orderNumber
    }
}
