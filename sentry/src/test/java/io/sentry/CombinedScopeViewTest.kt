package io.sentry

import io.sentry.protocol.Device
import io.sentry.protocol.Request
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import io.sentry.test.createTestScopes
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertNotEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class CombinedScopeViewTest {

    private class Fixture {
        lateinit var globalScope: IScope
        lateinit var isolationScope: IScope
        lateinit var scope: IScope
        lateinit var options: SentryOptions
        lateinit var scopes: IScopes

        fun getSut(options: SentryOptions = SentryOptions()): CombinedScopeView {
            options.dsn = "https://key@sentry.io/proj"
            options.release = "0.1"
            this.options = options
            globalScope = Scope(options)
            isolationScope = Scope(options)
            scope = Scope(options)
            scopes = createTestScopes(options, scope = scope, isolationScope = isolationScope, globalScope = globalScope)

            return CombinedScopeView(globalScope, isolationScope, scope)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `adds breadcrumbs from all scopes in sorted order`() {
        val combined = fixture.getSut()

        fixture.globalScope.addBreadcrumb(Breadcrumb.info("global 1"))
        fixture.isolationScope.addBreadcrumb(Breadcrumb.info("isolation 1"))
        fixture.scope.addBreadcrumb(Breadcrumb.info("current 1"))

        fixture.globalScope.addBreadcrumb(Breadcrumb.info("global 2"))
        fixture.isolationScope.addBreadcrumb(Breadcrumb.info("isolation 2"))
        fixture.scope.addBreadcrumb(Breadcrumb.info("current 2"))

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
        val combined = fixture.getSut(options)

        fixture.globalScope.addBreadcrumb(Breadcrumb.info("global 1"))
        fixture.isolationScope.addBreadcrumb(Breadcrumb.info("isolation 1"))
        fixture.scope.addBreadcrumb(Breadcrumb.info("current 1"))

        fixture.globalScope.addBreadcrumb(Breadcrumb.info("global 2"))
        fixture.isolationScope.addBreadcrumb(Breadcrumb.info("isolation 2"))
        fixture.scope.addBreadcrumb(Breadcrumb.info("current 2"))

        val breadcrumbs = combined.breadcrumbs
//        assertEquals("global 1", breadcrumbs.poll().message) <-- was dropped
        assertEquals("isolation 1", breadcrumbs.poll().message)
        assertEquals("current 1", breadcrumbs.poll().message)
        assertEquals("global 2", breadcrumbs.poll().message)
        assertEquals("isolation 2", breadcrumbs.poll().message)
        assertEquals("current 2", breadcrumbs.poll().message)

        fixture.scope.addBreadcrumb(Breadcrumb.info("current 3"))
        fixture.scope.addBreadcrumb(Breadcrumb.info("current 4"))

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
    fun `can add breadcrumb with hint`() {
        var capturedHint: Hint? = null
        val combined = fixture.getSut(
            SentryOptions().also {
                it.beforeBreadcrumb =
                    SentryOptions.BeforeBreadcrumbCallback { breadcrumb: Breadcrumb, hint: Hint ->
                        capturedHint = hint
                        breadcrumb
                    }
            }
        )

        combined.addBreadcrumb(Breadcrumb.info("aBreadcrumb"), Hint().also { it.set("aTest", "aValue") })

        assertNotNull(capturedHint)
        assertEquals("aValue", capturedHint?.get("aTest"))

        val breadcrumbs = combined.breadcrumbs
        assertEquals(1, breadcrumbs.size)
        assertEquals("aBreadcrumb", breadcrumbs.first().message)
    }

    @Test
    fun `adds breadcrumb to default scope`() {
        val combined = fixture.getSut()
        combined.addBreadcrumb(Breadcrumb.info("aBreadcrumb"))

        assertEquals(ScopeType.ISOLATION, combined.options.defaultScopeType)
        assertEquals(0, fixture.scope.breadcrumbs.size)
        assertEquals(1, fixture.isolationScope.breadcrumbs.size)
        assertEquals(0, fixture.globalScope.breadcrumbs.size)
    }

    @Test
    fun `clears breadcrumbs only from default scope`() {
        val combined = fixture.getSut()
        fixture.scope.addBreadcrumb(Breadcrumb.info("scopeBreadcrumb"))
        fixture.isolationScope.addBreadcrumb(Breadcrumb.info("isolationBreadcrumb"))
        fixture.globalScope.addBreadcrumb(Breadcrumb.info("globalBreadcrumb"))

        combined.clearBreadcrumbs()

        assertEquals(ScopeType.ISOLATION, combined.options.defaultScopeType)
        assertEquals(1, fixture.scope.breadcrumbs.size)
        assertEquals(0, fixture.isolationScope.breadcrumbs.size)
        assertEquals(1, fixture.globalScope.breadcrumbs.size)
    }

    @Test
    fun `event processors from options are not returned`() {
        val options = SentryOptions().also {
            it.addEventProcessor(MainEventProcessor(it))
        }
        val combined = fixture.getSut(options)

        assertEquals(0, combined.eventProcessors.size)
    }

    @Test
    fun `event processors from all scopes are returned in order`() {
        val combined = fixture.getSut()

        val first = TestEventProcessor(0).also { fixture.scope.addEventProcessor(it) }
        val second = TestEventProcessor(1000).also { fixture.globalScope.addEventProcessor(it) }
        val third = TestEventProcessor(2000).also { fixture.isolationScope.addEventProcessor(it) }
        val fourth = TestEventProcessor(3000).also { fixture.scope.addEventProcessor(it) }

        val eventProcessors = combined.eventProcessors

        assertEquals(first, eventProcessors[0])
        assertEquals(second, eventProcessors[1])
        assertEquals(third, eventProcessors[2])
        assertEquals(fourth, eventProcessors[3])
    }

    @Test
    fun `adds event processor to default scope`() {
        val combined = fixture.getSut()

        val eventProcessor = MainEventProcessor(fixture.options)
        combined.addEventProcessor(eventProcessor)

        assertEquals(ScopeType.ISOLATION, combined.options.defaultScopeType)
        assertFalse(fixture.scope.eventProcessors.contains(eventProcessor))
        assertTrue(fixture.isolationScope.eventProcessors.contains(eventProcessor))
        assertFalse(fixture.globalScope.eventProcessors.contains(eventProcessor))
    }

    @Test
    fun `prefers level from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.level = SentryLevel.DEBUG
        fixture.isolationScope.level = SentryLevel.INFO
        fixture.globalScope.level = SentryLevel.WARNING

        assertEquals(SentryLevel.DEBUG, combined.level)
    }

    @Test
    fun `uses isolation scope level if none in current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.level = SentryLevel.INFO
        fixture.globalScope.level = SentryLevel.WARNING

        assertEquals(SentryLevel.INFO, combined.level)
    }

    @Test
    fun `uses global scope level if none in current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.level = SentryLevel.WARNING

        assertEquals(SentryLevel.WARNING, combined.level)
    }

    @Test
    fun `returns null level if none in any scope`() {
        val combined = fixture.getSut()

        assertNull(combined.level)
    }

    @Test
    fun `setLevel modifies default scope`() {
        val combined = fixture.getSut()
        combined.level = SentryLevel.ERROR

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.level)
        assertEquals(SentryLevel.ERROR, fixture.isolationScope.level)
        assertNull(fixture.globalScope.level)
    }

    @Test
    fun `prefers transaction name from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.setTransaction("scopeTransaction")
        fixture.isolationScope.setTransaction("isolationTransaction")
        fixture.globalScope.setTransaction("globalTransaction")

        assertEquals("scopeTransaction", combined.transactionName)
    }

    @Test
    fun `uses isolation transaction name if none in current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.setTransaction("isolationTransaction")
        fixture.globalScope.setTransaction("globalTransaction")

        assertEquals("isolationTransaction", combined.transactionName)
    }

    @Test
    fun `uses global transaction name if none in current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.setTransaction("globalTransaction")

        assertEquals("globalTransaction", combined.transactionName)
    }

    @Test
    fun `returns null transaction name if none in any scope`() {
        val combined = fixture.getSut()

        assertNull(combined.transactionName)
    }

    @Test
    fun `setTransaction(String) modifies default scope`() {
        val combined = fixture.getSut()
        combined.setTransaction("aTransaction")

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.transactionName)
        assertEquals("aTransaction", fixture.isolationScope.transactionName)
        assertNull(fixture.globalScope.transactionName)
    }

    @Test
    fun `prefers transaction and span from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.setTransaction(createTransaction("scopeTransaction"))
        fixture.isolationScope.setTransaction(createTransaction("isolationTransaction"))
        fixture.globalScope.setTransaction(createTransaction("globalTransaction"))

        assertEquals("scopeTransaction", combined.transaction!!.name)
        assertEquals("scopeTransactionSpan", combined.span!!.operation)
    }

    @Test
    fun `uses isolation scope transaction and span if none in current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.setTransaction(createTransaction("isolationTransaction"))
        fixture.globalScope.setTransaction(createTransaction("globalTransaction"))

        assertEquals("isolationTransaction", combined.transaction!!.name)
        assertEquals("isolationTransactionSpan", combined.span!!.operation)
    }

    @Test
    fun `uses global transaction and scope span if none in current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.setTransaction(createTransaction("globalTransaction"))

        assertEquals("globalTransaction", combined.transaction!!.name)
        assertEquals("globalTransactionSpan", combined.span!!.operation)
    }

    @Test
    fun `returns null transaction and span if none in any scope`() {
        val combined = fixture.getSut()

        assertNull(combined.transaction)
        assertNull(combined.span)
    }

    @Test
    fun `setTransaction(ITransaction) modifies default scope`() {
        val combined = fixture.getSut()
        val tx = createTransaction("aTransaction")
        combined.setTransaction(tx)

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.transaction)
        assertSame(tx, fixture.isolationScope.transaction)
        assertNull(fixture.globalScope.transaction)
    }

    @Test
    fun `clears transaction from default scope`() {
        val combined = fixture.getSut()
        fixture.scope.setTransaction(createTransaction("scopeTransaction"))
        fixture.isolationScope.setTransaction(createTransaction("isolationTransaction"))
        fixture.globalScope.setTransaction(createTransaction("globalTransaction"))

        combined.clearTransaction()

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNotNull(fixture.scope.transaction)
        assertNull(fixture.isolationScope.transaction)
        assertNotNull(fixture.globalScope.transaction)
    }

    @Test
    fun `prefers user from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.user = User().also { it.name = "scopeUser" }
        fixture.isolationScope.user = User().also { it.name = "isolationUser" }
        fixture.globalScope.user = User().also { it.name = "globalUser" }

        assertEquals("scopeUser", combined.user!!.name)
    }

    @Test
    fun `uses isolation scope user if none in current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.user = User().also { it.name = "isolationUser" }
        fixture.globalScope.user = User().also { it.name = "globalUser" }

        assertEquals("isolationUser", combined.user!!.name)
    }

    @Test
    fun `uses global scope user if none in current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.user = User().also { it.name = "globalUser" }

        assertEquals("globalUser", combined.user!!.name)
    }

    @Test
    fun `returns null user if none in any scope`() {
        val combined = fixture.getSut()

        assertNull(combined.user)
    }

    @Test
    fun `set user modifies default scope`() {
        val combined = fixture.getSut()
        val user = User().also { it.name = "aUser" }
        combined.user = user

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.user)
        assertSame(user, fixture.isolationScope.user)
        assertNull(fixture.globalScope.user)
    }

    @Test
    fun `prefers screen from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.screen = "scopeScreen"
        fixture.isolationScope.screen = "isolationScreen"
        fixture.globalScope.screen = "globalScreen"

        assertEquals("scopeScreen", combined.screen)
    }

    @Test
    fun `uses isolation scope screen if none in current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.screen = "isolationScreen"
        fixture.globalScope.screen = "globalScreen"

        assertEquals("isolationScreen", combined.screen)
    }

    @Test
    fun `uses global scope screen if none in current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.screen = "globalScreen"

        assertEquals("globalScreen", combined.screen)
    }

    @Test
    fun `returns null screen if none in any scope`() {
        val combined = fixture.getSut()

        assertNull(combined.screen)
    }

    @Test
    fun `set screen modifies default scope`() {
        val combined = fixture.getSut()
        combined.screen = "aScreen"

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.screen)
        assertEquals("aScreen", fixture.isolationScope.screen)
        assertNull(fixture.globalScope.screen)
    }

    @Test
    fun `prefers request from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.request = Request().also { it.queryString = "scopeRequest" }
        fixture.isolationScope.request = Request().also { it.queryString = "isolationRequest" }
        fixture.globalScope.request = Request().also { it.queryString = "globalRequest" }

        assertEquals("scopeRequest", combined.request!!.queryString)
    }

    @Test
    fun `uses isolation scope request if none in current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.request = Request().also { it.queryString = "isolationRequest" }
        fixture.globalScope.request = Request().also { it.queryString = "globalRequest" }

        assertEquals("isolationRequest", combined.request!!.queryString)
    }

    @Test
    fun `uses global scope request if none in current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.request = Request().also { it.queryString = "globalRequest" }

        assertEquals("globalRequest", combined.request!!.queryString)
    }

    @Test
    fun `returns null request if none in any scope`() {
        val combined = fixture.getSut()

        assertNull(combined.request)
    }

    @Test
    fun `set request modifies default scope`() {
        val combined = fixture.getSut()
        val request = Request().also { it.queryString = "aRequest" }
        combined.request = request

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.request)
        assertSame(request, fixture.isolationScope.request)
        assertNull(fixture.globalScope.request)
    }

    @Test
    fun `clear removes from default scope`() {
        val combined = fixture.getSut()

        fixture.scope.level = SentryLevel.DEBUG
        fixture.isolationScope.level = SentryLevel.INFO
        fixture.globalScope.level = SentryLevel.WARNING

        combined.clear()

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNotNull(fixture.scope.level)
        assertNull(fixture.isolationScope.level)
        assertNotNull(fixture.globalScope.level)
    }

    @Test
    fun `tags are combined from all scopes`() {
        val combined = fixture.getSut()

        fixture.scope.setTag("scopeTag", "scopeValue")
        fixture.isolationScope.setTag("isolationTag", "isolationValue")
        fixture.globalScope.setTag("globalTag", "globalValue")

        val tags = combined.tags
        assertEquals("scopeValue", tags["scopeTag"])
        assertEquals("isolationValue", tags["isolationTag"])
        assertEquals("globalValue", tags["globalTag"])
    }

    @Test
    fun `setTag writes to default scope`() {
        val combined = fixture.getSut()
        combined.setTag("aTag", "aValue")

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.tags["aTag"])
        assertEquals("aValue", fixture.isolationScope.tags["aTag"])
        assertNull(fixture.globalScope.tags["aTag"])
    }

    @Test
    fun `prefer current scope value for tags with same key`() {
        val combined = fixture.getSut()

        fixture.scope.setTag("aTag", "scopeValue")
        fixture.isolationScope.setTag("aTag", "isolationValue")
        fixture.globalScope.setTag("aTag", "globalValue")

        assertEquals("scopeValue", combined.tags["aTag"])
    }

    @Test
    fun `uses isolation scope value for tags with same key if scope does not have it`() {
        val combined = fixture.getSut()

        fixture.isolationScope.setTag("aTag", "isolationValue")
        fixture.globalScope.setTag("aTag", "globalValue")

        assertEquals("isolationValue", combined.tags["aTag"])
    }

    @Test
    fun `uses global scope value for tags with same key if scope and isolation scope do not have it`() {
        val combined = fixture.getSut()

        fixture.globalScope.setTag("aTag", "globalValue")

        assertEquals("globalValue", combined.tags["aTag"])
    }

    @Test
    fun `removeTag removes from default scope`() {
        val combined = fixture.getSut()

        fixture.scope.setTag("aTag", "scopeValue")
        fixture.isolationScope.setTag("aTag", "isolationValue")
        fixture.globalScope.setTag("aTag", "globalValue")

        combined.removeTag("aTag")

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertEquals("scopeValue", fixture.scope.tags["aTag"])
        assertNull(fixture.isolationScope.tags["aTag"])
        assertEquals("globalValue", fixture.globalScope.tags["aTag"])
    }

    @Test
    fun `extras are combined from all scopes`() {
        val combined = fixture.getSut()

        fixture.scope.setExtra("scopeExtra", "scopeValue")
        fixture.isolationScope.setExtra("isolationExtra", "isolationValue")
        fixture.globalScope.setExtra("globalExtra", "globalValue")

        val extras = combined.extras
        assertEquals("scopeValue", extras["scopeExtra"])
        assertEquals("isolationValue", extras["isolationExtra"])
        assertEquals("globalValue", extras["globalExtra"])
    }

    @Test
    fun `setExtra writes to default scope`() {
        val combined = fixture.getSut()
        combined.setExtra("someExtra", "aValue")

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.extras["someExtra"])
        assertEquals("aValue", fixture.isolationScope.extras["someExtra"])
        assertNull(fixture.globalScope.extras["someExtra"])
    }

    @Test
    fun `prefer current scope value for extras with same key`() {
        val combined = fixture.getSut()

        fixture.scope.setExtra("someExtra", "scopeValue")
        fixture.isolationScope.setExtra("someExtra", "isolationValue")
        fixture.globalScope.setExtra("someExtra", "globalValue")

        assertEquals("scopeValue", combined.extras["someExtra"])
    }

    @Test
    fun `uses isolation scope value for extras with same key if scope does not have it`() {
        val combined = fixture.getSut()

        fixture.isolationScope.setExtra("someExtra", "isolationValue")
        fixture.globalScope.setExtra("someExtra", "globalValue")

        assertEquals("isolationValue", combined.extras["someExtra"])
    }

    @Test
    fun `uses global scope value for extras with same key if scope and isolation scope do not have it`() {
        val combined = fixture.getSut()

        fixture.globalScope.setExtra("someExtra", "globalValue")

        assertEquals("globalValue", combined.extras["someExtra"])
    }

    @Test
    fun `removeExtra removes from default scope`() {
        val combined = fixture.getSut()

        fixture.scope.setExtra("someExtra", "scopeValue")
        fixture.isolationScope.setExtra("someExtra", "isolationValue")
        fixture.globalScope.setExtra("someExtra", "globalValue")

        combined.removeExtra("someExtra")

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertEquals("scopeValue", fixture.scope.extras["someExtra"])
        assertNull(fixture.isolationScope.extras["someExtra"])
        assertEquals("globalValue", fixture.globalScope.extras["someExtra"])
    }

    @Test
    fun `combines context from all scopes`() {
        val combined = fixture.getSut()
        fixture.scope.setContexts("scopeContext", "scopeValue")
        fixture.isolationScope.setContexts("isolationContext", "isolationValue")
        fixture.globalScope.setContexts("globalContext", "globalValue")

        val contexts = combined.contexts
        assertEquals(mapOf("value" to "scopeValue"), contexts["scopeContext"])
    }

    @Test
    fun `current scope context overrides context of other scopes`() {
        val combined = fixture.getSut()
        fixture.scope.setContexts("someContext", "scopeValue")
        fixture.isolationScope.setContexts("someContext", "isolationValue")
        fixture.globalScope.setContexts("someContext", "globalValue")

        val contexts = combined.contexts
        assertEquals(mapOf("value" to "scopeValue"), contexts["someContext"])
    }

    @Test
    fun `isolation scope context overrides global context`() {
        val combined = fixture.getSut()
        fixture.isolationScope.setContexts("someContext", "isolationValue")
        fixture.globalScope.setContexts("someContext", "globalValue")

        val contexts = combined.contexts
        assertEquals(mapOf("value" to "isolationValue"), contexts["someContext"])
    }

    @Test
    fun `setContexts writes to default scope`() {
        val combined = fixture.getSut()
        combined.setContexts("aString", "stringValue")
        combined.setContexts("aChar", 'c')
        combined.setContexts("aNumber", 1)
        combined.setContexts("someObject", Device().also { it.brand = "someDeviceBrand" })
        combined.setContexts("someArray", arrayOf("a", "b"))
        combined.setContexts("someList", listOf("c", "d", "e"))

        assertNull(fixture.scope.contexts["aString"])
        assertNull(fixture.scope.contexts["aChar"])
        assertNull(fixture.scope.contexts["aNumber"])
        assertNull(fixture.scope.contexts["someObject"])
        assertNull(fixture.scope.contexts["someArray"])
        assertNull(fixture.scope.contexts["someList"])

        assertEquals(mapOf("value" to "stringValue"), fixture.isolationScope.contexts["aString"])
        assertEquals(mapOf("value" to 'c'), fixture.isolationScope.contexts["aChar"])
        assertEquals(mapOf("value" to 1), fixture.isolationScope.contexts["aNumber"])
        assertEquals("someDeviceBrand", (fixture.isolationScope.contexts["someObject"] as? Device)?.brand)
        val arrayValue = (fixture.isolationScope.contexts["someArray"] as? Map<String, Any>)?.get("value") as? Array<String>
        assertEquals(2, arrayValue?.size)
        assertEquals("a", arrayValue?.get(0))
        assertEquals("b", arrayValue?.get(1))
        val listValue = (fixture.isolationScope.contexts["someList"] as? Map<String, Any>)?.get("value") as? List<String>
        assertEquals(3, listValue?.size)
        assertEquals("c", listValue?.get(0))
        assertEquals("d", listValue?.get(1))
        assertEquals("e", listValue?.get(2))

        assertNull(fixture.globalScope.contexts["aString"])
        assertNull(fixture.globalScope.contexts["aChar"])
        assertNull(fixture.globalScope.contexts["aNumber"])
        assertNull(fixture.globalScope.contexts["someObject"])
        assertNull(fixture.globalScope.contexts["someArray"])
        assertNull(fixture.globalScope.contexts["someList"])
    }

    @Test
    fun `combines attachments from all scopes`() {
        val combined = fixture.getSut()

        fixture.scope.addAttachment(createAttachment("scopeAttachment.png"))
        fixture.isolationScope.addAttachment(createAttachment("isolationAttachment.png"))
        fixture.globalScope.addAttachment(createAttachment("globalAttachment.png"))

        val attachments = combined.attachments
        assertNotNull(attachments.firstOrNull { it.filename == "scopeAttachment.png" })
        assertNotNull(attachments.firstOrNull { it.filename == "isolationAttachment.png" })
        assertNotNull(attachments.firstOrNull { it.filename == "globalAttachment.png" })
    }

    @Test
    fun `adds attachment to default scope`() {
        val combined = fixture.getSut()
        combined.addAttachment(createAttachment("someAttachment.png"))

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.attachments.firstOrNull { it.filename == "someAttachment.png" })
        assertNotNull(fixture.isolationScope.attachments.firstOrNull { it.filename == "someAttachment.png" })
        assertNull(fixture.globalScope.attachments.firstOrNull { it.filename == "someAttachment.png" })
    }

    @Test
    fun `clears attachments only from default scope`() {
        val combined = fixture.getSut()

        fixture.scope.addAttachment(createAttachment("scopeAttachment.png"))
        fixture.isolationScope.addAttachment(createAttachment("isolationAttachment.png"))
        fixture.globalScope.addAttachment(createAttachment("globalAttachment.png"))

        combined.clearAttachments()

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNotNull(fixture.scope.attachments.firstOrNull { it.filename == "scopeAttachment.png" })
        assertNull(fixture.isolationScope.attachments.firstOrNull { it.filename == "isolationAttachment.png" })
        assertNotNull(fixture.globalScope.attachments.firstOrNull { it.filename == "globalAttachment.png" })
    }

    @Test
    fun `returns options from global scope`() {
        val scopeOptions = SentryOptions().also { it.dist = "scopeDist" }
        val isolationOptions = SentryOptions().also { it.dist = "isolationDist" }
        val globalOptions = SentryOptions().also { it.dist = "globalDist" }

        val combined = CombinedScopeView(Scope(globalOptions), Scope(isolationOptions), Scope(scopeOptions))
        assertEquals("globalDist", combined.options.dist)
    }

    @Test
    fun `replaces options on global scope`() {
        val scopeOptions = SentryOptions().also { it.dist = "scopeDist" }
        val isolationOptions = SentryOptions().also { it.dist = "isolationDist" }
        val globalOptions = SentryOptions().also { it.dist = "globalDist" }

        val globalScope = Scope(globalOptions)
        val isolationScope = Scope(isolationOptions)
        val scope = Scope(scopeOptions)
        val combined = CombinedScopeView(globalScope, isolationScope, scope)

        val newOptions = SentryOptions().also { it.dist = "newDist" }
        combined.replaceOptions(newOptions)

        assertEquals("scopeDist", scope.options.dist)
        assertEquals("isolationDist", isolationScope.options.dist)
        assertEquals("newDist", globalScope.options.dist)
    }

    @Test
    fun `prefers client from scope`() {
        val combined = fixture.getSut()

        val scopeClient = SentryClient(fixture.options)
        fixture.scope.bindClient(scopeClient)

        val isolationClient = SentryClient(fixture.options)
        fixture.isolationScope.bindClient(isolationClient)

        val globalClient = SentryClient(fixture.options)
        fixture.globalScope.bindClient(globalClient)

        assertSame(scopeClient, combined.client)
    }

    @Test
    fun `uses isolation scope client if noop on current scope`() {
        val combined = fixture.getSut()
        fixture.scope.bindClient(NoOpSentryClient.getInstance())
        fixture.isolationScope.bindClient(NoOpSentryClient.getInstance())
        fixture.globalScope.bindClient(NoOpSentryClient.getInstance())

        val isolationClient = SentryClient(fixture.options)
        fixture.isolationScope.bindClient(isolationClient)

        val globalClient = SentryClient(fixture.options)
        fixture.globalScope.bindClient(globalClient)

        assertSame(isolationClient, combined.client)
    }

    @Test
    fun `uses global scope client if noop on current and isolation scope`() {
        val combined = fixture.getSut()
        fixture.scope.bindClient(NoOpSentryClient.getInstance())
        fixture.isolationScope.bindClient(NoOpSentryClient.getInstance())
        fixture.globalScope.bindClient(NoOpSentryClient.getInstance())

        val globalClient = SentryClient(fixture.options)
        fixture.globalScope.bindClient(globalClient)

        assertSame(globalClient, combined.client)
    }

    @Test
    fun `binds client to default scope`() {
        val combined = fixture.getSut()
        fixture.scope.bindClient(NoOpSentryClient.getInstance())
        fixture.isolationScope.bindClient(NoOpSentryClient.getInstance())
        fixture.globalScope.bindClient(NoOpSentryClient.getInstance())

        val client = SentryClient(fixture.options)
        combined.bindClient(client)

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertTrue(fixture.scope.client is NoOpSentryClient)
        assertSame(client, fixture.isolationScope.client)
        assertTrue(fixture.globalScope.client is NoOpSentryClient)
    }

    @Test
    fun `getSpecificScope(null) returns scope defined in options CURRENT`() {
        val combined = fixture.getSut(SentryOptions().also { it.defaultScopeType = ScopeType.CURRENT })
        assertSame(fixture.scope, combined.getSpecificScope(null))
    }

    @Test
    fun `getSpecificScope(null) returns scope defined in options ISOLATION`() {
        val combined = fixture.getSut(SentryOptions().also { it.defaultScopeType = ScopeType.ISOLATION })
        assertSame(fixture.isolationScope, combined.getSpecificScope(null))
    }

    @Test
    fun `getSpecificScope(null) returns scope defined in options GLOBAL`() {
        val combined = fixture.getSut(SentryOptions().also { it.defaultScopeType = ScopeType.GLOBAL })
        assertSame(fixture.globalScope, combined.getSpecificScope(null))
    }

    @Test
    fun `getSpecificScope(CURRENT) returns current scope`() {
        val combined = fixture.getSut(SentryOptions().also { it.defaultScopeType = ScopeType.ISOLATION })
        assertSame(fixture.scope, combined.getSpecificScope(ScopeType.CURRENT))
    }

    @Test
    fun `getSpecificScope(ISOLATION) returns isolation scope`() {
        val combined = fixture.getSut(SentryOptions().also { it.defaultScopeType = ScopeType.CURRENT })
        assertSame(fixture.isolationScope, combined.getSpecificScope(ScopeType.ISOLATION))
    }

    @Test
    fun `getSpecificScope(GLOBAL) returns global scope`() {
        val combined = fixture.getSut(SentryOptions().also { it.defaultScopeType = ScopeType.CURRENT })
        assertSame(fixture.globalScope, combined.getSpecificScope(ScopeType.GLOBAL))
    }

    @Test
    fun `forwards setSpanContext to global scope`() {
        val scope = mock<IScope>()
        val isolationScope = mock<IScope>()
        val globalScope = mock<IScope>()
        val combined = CombinedScopeView(globalScope, isolationScope, scope)

        val options = SentryOptions().also { it.dsn = "https://key@sentry.io/proj" }
        whenever(globalScope.options).thenReturn(options)

        val exception = RuntimeException("someEx")
        val transaction = createTransaction("aTransaction", createTestScopes(options = options, scope = scope, isolationScope = isolationScope, globalScope = globalScope))
        combined.setSpanContext(exception, transaction, "aTransaction")

        verify(scope, never()).setSpanContext(any(), any(), any())
        verify(isolationScope, never()).setSpanContext(any(), any(), any())
        verify(globalScope).setSpanContext(same(exception), same(transaction), eq("aTransaction"))
    }

    @Test
    fun `withTransaction uses default scope`() {
        val combined = fixture.getSut()
        fixture.scope.setTransaction(createTransaction("scopeTransaction"))
        fixture.isolationScope.setTransaction(createTransaction("isolationTransaction"))
        fixture.globalScope.setTransaction(createTransaction("globalTransaction"))

        var capturedTransaction: ITransaction? = null
        combined.withTransaction { transaction ->
            capturedTransaction = transaction
        }

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertEquals("isolationTransaction", capturedTransaction?.name)
    }

    @Test
    fun `forwards assignTraceContext to global scope`() {
        val scope = mock<IScope>()
        val isolationScope = mock<IScope>()
        val globalScope = mock<IScope>()
        val combined = CombinedScopeView(globalScope, isolationScope, scope)

        val event = SentryEvent()
        combined.assignTraceContext(event)

        verify(scope, never()).assignTraceContext(any())
        verify(isolationScope, never()).assignTraceContext(any())
        verify(globalScope).assignTraceContext(same(event))
    }

    @Test
    fun `retrieves last event id from global scope`() {
        val combined = fixture.getSut()
        fixture.scope.lastEventId = SentryId("c81d4e2e-bcf2-11e6-869b-7df92533d2dc")
        fixture.isolationScope.lastEventId = SentryId("d81d4e2e-bcf2-11e6-869b-7df92533d2dd")
        fixture.globalScope.lastEventId = SentryId("e81d4e2e-bcf2-11e6-869b-7df92533d2de")

        assertEquals("e81d4e2ebcf211e6869b7df92533d2de", combined.lastEventId.toString())
    }

    @Test
    fun `sets last event id on all scopes`() {
        val combined = fixture.getSut()
        combined.lastEventId = SentryId("c81d4e2e-bcf2-11e6-869b-7df92533d2db")

        assertEquals("c81d4e2ebcf211e6869b7df92533d2db", fixture.scope.lastEventId.toString())
        assertEquals("c81d4e2ebcf211e6869b7df92533d2db", fixture.isolationScope.lastEventId.toString())
        assertEquals("c81d4e2ebcf211e6869b7df92533d2db", fixture.globalScope.lastEventId.toString())
    }

    @Test
    fun `retrieves propagation context from default scope`() {
        val combined = fixture.getSut()
        fixture.scope.propagationContext = PropagationContext().also { it.traceId = SentryId("c81d4e2e-bcf2-11e6-869b-7df92533d2dc") }
        fixture.isolationScope.propagationContext = PropagationContext().also { it.traceId = SentryId("d81d4e2e-bcf2-11e6-869b-7df92533d2dd") }
        fixture.globalScope.propagationContext = PropagationContext().also { it.traceId = SentryId("e81d4e2e-bcf2-11e6-869b-7df92533d2de") }

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertEquals("d81d4e2ebcf211e6869b7df92533d2dd", combined.propagationContext.traceId.toString())
    }

    @Test
    fun `sets propagation context on default scope`() {
        val combined = fixture.getSut()

        combined.propagationContext = PropagationContext().also { it.traceId = SentryId("c81d4e2e-bcf2-11e6-869b-7df92533d2db") }

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNotEquals("c81d4e2ebcf211e6869b7df92533d2db", fixture.scope.propagationContext.traceId.toString())
        assertEquals("c81d4e2ebcf211e6869b7df92533d2db", fixture.isolationScope.propagationContext.traceId.toString())
        assertNotEquals("c81d4e2ebcf211e6869b7df92533d2db", fixture.globalScope.propagationContext.traceId.toString())
    }

    @Test
    fun `withPropagationContext uses default scope`() {
        val combined = fixture.getSut()
        fixture.scope.propagationContext = PropagationContext().also { it.traceId = SentryId("c81d4e2e-bcf2-11e6-869b-7df92533d2dc") }
        fixture.isolationScope.propagationContext = PropagationContext().also { it.traceId = SentryId("d81d4e2e-bcf2-11e6-869b-7df92533d2dd") }
        fixture.globalScope.propagationContext = PropagationContext().also { it.traceId = SentryId("e81d4e2e-bcf2-11e6-869b-7df92533d2de") }

        var capturedPropagationContext: PropagationContext? = null
        combined.withPropagationContext { propagationContext ->
            capturedPropagationContext = propagationContext
        }

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertEquals("d81d4e2ebcf211e6869b7df92533d2dd", capturedPropagationContext?.traceId.toString())
    }

    @Test
    fun `starts session on default scope`() {
        val combined = fixture.getSut()

        combined.startSession()

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNull(fixture.scope.session)
        assertNotNull(fixture.isolationScope.session)
        assertNull(fixture.globalScope.session)
    }

    @Test
    fun `ends session on default scope`() {
        val combined = fixture.getSut()
        fixture.scope.startSession()
        fixture.isolationScope.startSession()
        fixture.globalScope.startSession()

        combined.endSession()

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertNotNull(fixture.scope.session)
        assertNull(fixture.isolationScope.session)
        assertNotNull(fixture.globalScope.session)
    }

    @Test
    fun `prefers session from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.startSession()
        fixture.isolationScope.startSession()
        fixture.globalScope.startSession()

        assertSame(fixture.scope.session, combined.session)
    }

    @Test
    fun `uses isolation scope session if none on current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.startSession()
        fixture.globalScope.startSession()

        assertSame(fixture.isolationScope.session, combined.session)
    }

    @Test
    fun `uses global scope session if none on current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.startSession()

        assertSame(fixture.globalScope.session, combined.session)
    }

    @Test
    fun `withSession uses default scope`() {
        val combined = fixture.getSut()
        fixture.scope.startSession()
        fixture.isolationScope.startSession()
        fixture.globalScope.startSession()

        var capturedSession: Session? = null
        combined.withSession { session ->
            capturedSession = session
        }

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertSame(fixture.isolationScope.session, capturedSession)
    }

    @Test
    fun `sets fingerprint on default scope`() {
        val combined = fixture.getSut()
        combined.fingerprint = listOf("aFingerprint")

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertEquals(0, fixture.scope.fingerprint.size)
        assertEquals(1, fixture.isolationScope.fingerprint.size)
        assertEquals(0, fixture.globalScope.fingerprint.size)
    }

    @Test
    fun `prefers fingerprint from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.fingerprint = listOf("scopeFingerprint")
        fixture.isolationScope.fingerprint = listOf("isolationFingerprint")
        fixture.globalScope.fingerprint = listOf("globalFingerprint")

        assertEquals(listOf("scopeFingerprint"), combined.fingerprint)
    }

    @Test
    fun `uses isolation scope fingerprint if current scope does not have one`() {
        val combined = fixture.getSut()
        fixture.isolationScope.fingerprint = listOf("isolationFingerprint")
        fixture.globalScope.fingerprint = listOf("globalFingerprint")

        assertEquals(listOf("isolationFingerprint"), combined.fingerprint)
    }

    @Test
    fun `uses global scope fingerprint if current and isolation scope do not have one`() {
        val combined = fixture.getSut()
        fixture.globalScope.fingerprint = listOf("globalFingerprint")

        assertEquals(listOf("globalFingerprint"), combined.fingerprint)
    }

    @Test
    fun `prefers replay ID from current scope`() {
        val combined = fixture.getSut()
        fixture.scope.replayId = SentryId("a9118105af4a2d42b4124532cd1065fa")
        fixture.isolationScope.replayId = SentryId("e9118105af4a2d42b4124532cd1065fe")
        fixture.globalScope.replayId = SentryId("f9118105af4a2d42b4124532cd1065ff")

        assertEquals("a9118105af4a2d42b4124532cd1065fa", combined.replayId.toString())
    }

    @Test
    fun `uses isolation scope replay ID if none in current scope`() {
        val combined = fixture.getSut()
        fixture.isolationScope.replayId = SentryId("e9118105af4a2d42b4124532cd1065fe")
        fixture.globalScope.replayId = SentryId("f9118105af4a2d42b4124532cd1065ff")

        assertEquals("e9118105af4a2d42b4124532cd1065fe", combined.replayId.toString())
    }

    @Test
    fun `uses global scope replay ID if none in current or isolation scope`() {
        val combined = fixture.getSut()
        fixture.globalScope.replayId = SentryId("f9118105af4a2d42b4124532cd1065ff")

        assertEquals("f9118105af4a2d42b4124532cd1065ff", combined.replayId.toString())
    }

    @Test
    fun `returns empty replay ID if none in any scope`() {
        val combined = fixture.getSut()

        assertEquals(SentryId.EMPTY_ID, combined.replayId)
    }

    @Test
    fun `set replay ID modifies default scope`() {
        val combined = fixture.getSut()
        combined.replayId = SentryId("b9118105af4a2d42b4124532cd1065fb")

        assertEquals(ScopeType.ISOLATION, fixture.options.defaultScopeType)
        assertEquals(SentryId.EMPTY_ID, fixture.scope.replayId)
        assertEquals("b9118105af4a2d42b4124532cd1065fb", fixture.isolationScope.replayId.toString())
        assertEquals(SentryId.EMPTY_ID, fixture.globalScope.replayId)
    }

    @Test
    fun `null tags do not cause NPE`() {
        val scope = fixture.getSut()
        scope.setTag("k", "oldvalue")
        scope.setTag(null, null)
        scope.setTag("k", null)
        scope.setTag(null, "v")
        scope.removeTag(null)
        kotlin.test.assertTrue(scope.tags.isEmpty())
    }

    @Test
    fun `null extras do not cause NPE`() {
        val scope = fixture.getSut()
        scope.setExtra("k", "oldvalue")
        scope.setExtra(null, null)
        scope.setExtra("k", null)
        scope.setExtra(null, "v")
        scope.removeExtra(null)
        kotlin.test.assertTrue(scope.extras.isEmpty())
    }

    @Test
    fun `null contexts do not cause NPE`() {
        val scope = fixture.getSut()

        scope.setContexts("obj", null as Any?)
        scope.setContexts("bool", true)
        scope.setContexts("string", "hello")
        scope.setContexts("num", 100)
        scope.setContexts("list", listOf("a", "b"))
        scope.setContexts("array", arrayOf("c", "d"))
        scope.setContexts("char", 'z')

        kotlin.test.assertFalse(scope.contexts.isEmpty)

        scope.setContexts(null, null as Any?)
        scope.setContexts(null, null as Boolean?)
        scope.setContexts(null, null as String?)
        scope.setContexts(null, null as Number?)
        scope.setContexts(null, null as List<Any>?)
        scope.setContexts(null, null as Array<Any>?)
        scope.setContexts(null, null as Character?)

        scope.setContexts("obj", null as Any?)
        scope.setContexts("bool", null as Boolean?)
        scope.setContexts("string", null as String?)
        scope.setContexts("num", null as Number?)
        scope.setContexts("list", null as List<Any>?)
        scope.setContexts("array", null as Array<Any>?)
        scope.setContexts("char", null as Character?)

        scope.removeContexts(null)

        kotlin.test.assertTrue(scope.contexts.isEmpty)
    }

    private fun createTransaction(name: String, scopes: Scopes? = null): ITransaction {
        val scopesToUse = scopes ?: fixture.scopes
        return SentryTracer(TransactionContext(name, "op", TracesSamplingDecision(true)), scopesToUse).also {
            it.startChild("${name}Span")
        }
    }

    private fun createAttachment(name: String): Attachment {
        return Attachment("a".toByteArray(), name, "image/png", false)
    }

    class TestEventProcessor(val orderNumber: Long?) : EventProcessor {
        override fun getOrder() = orderNumber
    }
}
