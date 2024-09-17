package io.sentry

import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class HubAdapterTest {

    val hub: Hub = mock()

    @BeforeTest
    fun `set up`() {
        Sentry.setCurrentHub(hub)
    }

    @AfterTest
    fun shutdown() {
        Sentry.close()
    }

    @Test fun `isEnabled calls Hub`() {
        HubAdapter.getInstance().isEnabled
        verify(hub).isEnabled
    }

    @Test fun `captureEvent calls Hub`() {
        val event = mock<SentryEvent>()
        val hint = mock<Hint>()
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().captureEvent(event, hint)
        verify(hub).captureEvent(eq(event), eq(hint))

        HubAdapter.getInstance().captureEvent(event, hint, scopeCallback)
        verify(hub).captureEvent(eq(event), eq(hint), eq(scopeCallback))
    }

    @Test fun `captureMessage calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        val sentryLevel = mock<SentryLevel>()
        HubAdapter.getInstance().captureMessage("message", sentryLevel)
        verify(hub).captureMessage(eq("message"), eq(sentryLevel))

        HubAdapter.getInstance().captureMessage("message", sentryLevel, scopeCallback)
        verify(hub).captureMessage(eq("message"), eq(sentryLevel), eq(scopeCallback))
    }

    @Test fun `captureEnvelope calls Hub`() {
        val envelope = mock<SentryEnvelope>()
        val hint = mock<Hint>()
        HubAdapter.getInstance().captureEnvelope(envelope, hint)
        verify(hub).captureEnvelope(eq(envelope), eq(hint))
    }

    @Test fun `captureException calls Hub`() {
        val throwable = mock<Throwable>()
        val hint = mock<Hint>()
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().captureException(throwable, hint)
        verify(hub).captureException(eq(throwable), eq(hint))

        HubAdapter.getInstance().captureException(throwable, hint, scopeCallback)
        verify(hub).captureException(eq(throwable), eq(hint), eq(scopeCallback))
    }

    @Test fun `captureUserFeedback calls Hub`() {
        val userFeedback = mock<UserFeedback>()
        HubAdapter.getInstance().captureUserFeedback(userFeedback)
        verify(hub).captureUserFeedback(eq(userFeedback))
    }

    @Test fun `captureCheckIn calls Hub`() {
        val checkIn = mock<CheckIn>()
        HubAdapter.getInstance().captureCheckIn(checkIn)
        verify(hub).captureCheckIn(eq(checkIn))
    }

    @Test fun `startSession calls Hub`() {
        HubAdapter.getInstance().startSession()
        verify(hub).startSession()
    }

    @Test fun `endSession calls Hub`() {
        HubAdapter.getInstance().endSession()
        verify(hub).endSession()
    }

    @Test fun `close calls Hub`() {
        HubAdapter.getInstance().close()
        verify(hub).close(false)
    }

    @Test fun `close with isRestarting true calls Hub with isRestarting false`() {
        HubAdapter.getInstance().close(true)
        verify(hub).close(false)
    }

    @Test fun `close with isRestarting false calls Hub with isRestarting false`() {
        HubAdapter.getInstance().close(false)
        verify(hub).close(false)
    }

    @Test fun `addBreadcrumb calls Hub`() {
        val breadcrumb = mock<Breadcrumb>()
        val hint = mock<Hint>()
        HubAdapter.getInstance().addBreadcrumb(breadcrumb, hint)
        verify(hub).addBreadcrumb(eq(breadcrumb), eq(hint))
    }

    @Test fun `setLevel calls Hub`() {
        val sentryLevel = mock<SentryLevel>()
        HubAdapter.getInstance().setLevel(sentryLevel)
        verify(hub).setLevel(eq(sentryLevel))
    }

    @Test fun `setTransaction calls Hub`() {
        HubAdapter.getInstance().setTransaction("transaction")
        verify(hub).setTransaction(eq("transaction"))
    }

    @Test fun `setUser calls Hub`() {
        val user = mock<User>()
        HubAdapter.getInstance().setUser(user)
        verify(hub).setUser(eq(user))
    }

    @Test fun `setFingerprint calls Hub`() {
        val fingerprint = ArrayList<String>()
        HubAdapter.getInstance().setFingerprint(fingerprint)
        verify(hub).setFingerprint(eq(fingerprint))
    }

    @Test fun `clearBreadcrumbs calls Hub`() {
        HubAdapter.getInstance().clearBreadcrumbs()
        verify(hub).clearBreadcrumbs()
    }

    @Test fun `setTag calls Hub`() {
        HubAdapter.getInstance().setTag("key", "value")
        verify(hub).setTag(eq("key"), eq("value"))
    }

    @Test fun `removeTag calls Hub`() {
        HubAdapter.getInstance().removeTag("key")
        verify(hub).removeTag(eq("key"))
    }

    @Test fun `setExtra calls Hub`() {
        HubAdapter.getInstance().setExtra("key", "value")
        verify(hub).setExtra(eq("key"), eq("value"))
    }

    @Test fun `removeExtra calls Hub`() {
        HubAdapter.getInstance().removeExtra("key")
        verify(hub).removeExtra(eq("key"))
    }

    @Test fun `getLastEventId calls Hub`() {
        HubAdapter.getInstance().lastEventId
        verify(hub).lastEventId
    }

    @Test fun `pushScope calls Hub`() {
        HubAdapter.getInstance().pushScope()
        verify(hub).pushScope()
    }

    @Test fun `popScope calls Hub`() {
        HubAdapter.getInstance().popScope()
        verify(hub).popScope()
    }

    @Test fun `withScope calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().withScope(scopeCallback)
        verify(hub).withScope(eq(scopeCallback))
    }

    @Test fun `configureScope calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().configureScope(scopeCallback)
        verify(hub).configureScope(eq(scopeCallback))
    }

    @Test fun `bindClient calls Hub`() {
        val client = mock<ISentryClient>()
        HubAdapter.getInstance().bindClient(client)
        verify(hub).bindClient(eq(client))
    }

    @Test fun `flush calls Hub`() {
        HubAdapter.getInstance().flush(1)
        verify(hub).flush(eq(1))
    }

    @Test fun `clone calls Hub`() {
        HubAdapter.getInstance().clone()
        verify(hub).clone()
    }

    @Test fun `captureTransaction calls Hub`() {
        val transaction = mock<SentryTransaction>()
        val traceContext = mock<TraceContext>()
        val hint = mock<Hint>()
        val profilingTraceData = mock<ProfilingTraceData>()
        HubAdapter.getInstance().captureTransaction(transaction, traceContext, hint, profilingTraceData)
        verify(hub).captureTransaction(eq(transaction), eq(traceContext), eq(hint), eq(profilingTraceData))
    }

    @Test fun `captureProfileChunk calls Hub`() {
        val profileChunk = mock<ProfileChunk>()
        HubAdapter.getInstance().captureProfileChunk(profileChunk)
        verify(hub).captureProfileChunk(eq(profileChunk))
    }

    @Test fun `startTransaction calls Hub`() {
        val transactionContext = mock<TransactionContext>()
        val samplingContext = mock<CustomSamplingContext>()
        val transactionOptions = mock<TransactionOptions>()
        HubAdapter.getInstance().startTransaction(transactionContext)
        verify(hub).startTransaction(eq(transactionContext), any<TransactionOptions>())

        reset(hub)

        HubAdapter.getInstance().startTransaction(transactionContext, transactionOptions)
        verify(hub).startTransaction(eq(transactionContext), eq(transactionOptions))
    }

    @Test fun `traceHeaders calls Hub`() {
        HubAdapter.getInstance().traceHeaders()
        verify(hub).traceHeaders()
    }

    @Test fun `setSpanContext calls Hub`() {
        val throwable = mock<Throwable>()
        val span = mock<ISpan>()
        HubAdapter.getInstance().setSpanContext(throwable, span, "transactionName")
        verify(hub).setSpanContext(eq(throwable), eq(span), eq("transactionName"))
    }

    @Test fun `getSpan calls Hub`() {
        HubAdapter.getInstance().span
        verify(hub).span
    }

    @Test fun `getTransaction calls Hub`() {
        HubAdapter.getInstance().transaction
        verify(hub).transaction
    }

    @Test fun `getOptions calls Hub`() {
        HubAdapter.getInstance().options
        verify(hub).options
    }

    @Test fun `isCrashedLastRun calls Hub`() {
        HubAdapter.getInstance().isCrashedLastRun
        verify(hub).isCrashedLastRun
    }

    @Test fun `reportFullyDisplayed calls Hub`() {
        HubAdapter.getInstance().reportFullyDisplayed()
        verify(hub).reportFullyDisplayed()
    }
}
