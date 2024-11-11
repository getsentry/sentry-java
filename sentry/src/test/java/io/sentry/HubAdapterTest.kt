package io.sentry

import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.test.createSentryClientMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class HubAdapterTest {

    val scopes: IScopes = mock()

    @BeforeTest
    fun `set up`() {
        Sentry.setCurrentScopes(scopes)
    }

    @AfterTest
    fun shutdown() {
        Sentry.close()
    }

    @Test fun `isEnabled calls Hub`() {
        HubAdapter.getInstance().isEnabled
        verify(scopes).isEnabled
    }

    @Test fun `captureEvent calls Hub`() {
        val event = mock<SentryEvent>()
        val hint = mock<Hint>()
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().captureEvent(event, hint)
        verify(scopes).captureEvent(eq(event), eq(hint))

        HubAdapter.getInstance().captureEvent(event, hint, scopeCallback)
        verify(scopes).captureEvent(eq(event), eq(hint), eq(scopeCallback))
    }

    @Test fun `captureMessage calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        val sentryLevel = mock<SentryLevel>()
        HubAdapter.getInstance().captureMessage("message", sentryLevel)
        verify(scopes).captureMessage(eq("message"), eq(sentryLevel))

        HubAdapter.getInstance().captureMessage("message", sentryLevel, scopeCallback)
        verify(scopes).captureMessage(eq("message"), eq(sentryLevel), eq(scopeCallback))
    }

    @Test fun `captureEnvelope calls Hub`() {
        val envelope = mock<SentryEnvelope>()
        val hint = mock<Hint>()
        HubAdapter.getInstance().captureEnvelope(envelope, hint)
        verify(scopes).captureEnvelope(eq(envelope), eq(hint))
    }

    @Test fun `captureException calls Hub`() {
        val throwable = mock<Throwable>()
        val hint = mock<Hint>()
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().captureException(throwable, hint)
        verify(scopes).captureException(eq(throwable), eq(hint))

        HubAdapter.getInstance().captureException(throwable, hint, scopeCallback)
        verify(scopes).captureException(eq(throwable), eq(hint), eq(scopeCallback))
    }

    @Test fun `captureUserFeedback calls Hub`() {
        val userFeedback = mock<UserFeedback>()
        HubAdapter.getInstance().captureUserFeedback(userFeedback)
        verify(scopes).captureUserFeedback(eq(userFeedback))
    }

    @Test fun `captureCheckIn calls Hub`() {
        val checkIn = mock<CheckIn>()
        HubAdapter.getInstance().captureCheckIn(checkIn)
        verify(scopes).captureCheckIn(eq(checkIn))
    }

    @Test fun `startSession calls Hub`() {
        HubAdapter.getInstance().startSession()
        verify(scopes).startSession()
    }

    @Test fun `endSession calls Hub`() {
        HubAdapter.getInstance().endSession()
        verify(scopes).endSession()
    }

    @Test fun `close calls Hub`() {
        HubAdapter.getInstance().close()
        verify(scopes).close(false)
    }

    @Test fun `close with isRestarting true calls Hub with isRestarting false`() {
        HubAdapter.getInstance().close(true)
        verify(scopes).close(false)
    }

    @Test fun `close with isRestarting false calls Hub with isRestarting false`() {
        HubAdapter.getInstance().close(false)
        verify(scopes).close(false)
    }

    @Test fun `addBreadcrumb calls Hub`() {
        val breadcrumb = mock<Breadcrumb>()
        val hint = mock<Hint>()
        HubAdapter.getInstance().addBreadcrumb(breadcrumb, hint)
        verify(scopes).addBreadcrumb(eq(breadcrumb), eq(hint))
    }

    @Test fun `setLevel calls Hub`() {
        val sentryLevel = mock<SentryLevel>()
        HubAdapter.getInstance().setLevel(sentryLevel)
        verify(scopes).setLevel(eq(sentryLevel))
    }

    @Test fun `setTransaction calls Hub`() {
        HubAdapter.getInstance().setTransaction("transaction")
        verify(scopes).setTransaction(eq("transaction"))
    }

    @Test fun `setUser calls Hub`() {
        val user = mock<User>()
        HubAdapter.getInstance().setUser(user)
        verify(scopes).setUser(eq(user))
    }

    @Test fun `setFingerprint calls Hub`() {
        val fingerprint = ArrayList<String>()
        HubAdapter.getInstance().setFingerprint(fingerprint)
        verify(scopes).setFingerprint(eq(fingerprint))
    }

    @Test fun `clearBreadcrumbs calls Hub`() {
        HubAdapter.getInstance().clearBreadcrumbs()
        verify(scopes).clearBreadcrumbs()
    }

    @Test fun `setTag calls Hub`() {
        HubAdapter.getInstance().setTag("key", "value")
        verify(scopes).setTag(eq("key"), eq("value"))
    }

    @Test fun `removeTag calls Hub`() {
        HubAdapter.getInstance().removeTag("key")
        verify(scopes).removeTag(eq("key"))
    }

    @Test fun `setExtra calls Hub`() {
        HubAdapter.getInstance().setExtra("key", "value")
        verify(scopes).setExtra(eq("key"), eq("value"))
    }

    @Test fun `removeExtra calls Hub`() {
        HubAdapter.getInstance().removeExtra("key")
        verify(scopes).removeExtra(eq("key"))
    }

    @Test fun `getLastEventId calls Hub`() {
        HubAdapter.getInstance().lastEventId
        verify(scopes).lastEventId
    }

    @Test fun `pushScope calls Hub`() {
        HubAdapter.getInstance().pushScope()
        verify(scopes).pushScope()
    }

    @Test fun `popScope calls Hub`() {
        HubAdapter.getInstance().popScope()
        verify(scopes).popScope()
    }

    @Test fun `withScope calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().withScope(scopeCallback)
        verify(scopes).withScope(eq(scopeCallback))
    }

    @Test fun `configureScope calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        HubAdapter.getInstance().configureScope(scopeCallback)
        verify(scopes).configureScope(anyOrNull(), eq(scopeCallback))
    }

    @Test fun `bindClient calls Hub`() {
        val client = createSentryClientMock()
        HubAdapter.getInstance().bindClient(client)
        verify(scopes).bindClient(eq(client))
    }

    @Test fun `flush calls Hub`() {
        HubAdapter.getInstance().flush(1)
        verify(scopes).flush(eq(1))
    }

    @Test fun `clone calls Hub`() {
        HubAdapter.getInstance().clone()
        verify(scopes).clone()
    }

    @Test fun `captureTransaction calls Hub`() {
        val transaction = mock<SentryTransaction>()
        val traceContext = mock<TraceContext>()
        val hint = mock<Hint>()
        val profilingTraceData = mock<ProfilingTraceData>()
        HubAdapter.getInstance().captureTransaction(transaction, traceContext, hint, profilingTraceData)
        verify(scopes).captureTransaction(eq(transaction), eq(traceContext), eq(hint), eq(profilingTraceData))
    }

    @Test fun `captureProfileChunk calls Hub`() {
        val profileChunk = mock<ProfileChunk>()
        HubAdapter.getInstance().captureProfileChunk(profileChunk)
        verify(scopes).captureProfileChunk(eq(profileChunk))
    }

    @Test fun `startTransaction calls Hub`() {
        val transactionContext = mock<TransactionContext>()
        val samplingContext = mock<CustomSamplingContext>()
        val transactionOptions = mock<TransactionOptions>()
        HubAdapter.getInstance().startTransaction(transactionContext)
        verify(scopes).startTransaction(eq(transactionContext), any<TransactionOptions>())

        reset(scopes)

        HubAdapter.getInstance().startTransaction(transactionContext, transactionOptions)
        verify(scopes).startTransaction(eq(transactionContext), eq(transactionOptions))
    }

    @Test fun `setSpanContext calls Hub`() {
        val throwable = mock<Throwable>()
        val span = mock<ISpan>()
        HubAdapter.getInstance().setSpanContext(throwable, span, "transactionName")
        verify(scopes).setSpanContext(eq(throwable), eq(span), eq("transactionName"))
    }

    @Test fun `getSpan calls Hub`() {
        HubAdapter.getInstance().span
        verify(scopes).span
    }

    @Test fun `getTransaction calls Hub`() {
        HubAdapter.getInstance().transaction
        verify(scopes).transaction
    }

    @Test fun `getOptions calls Hub`() {
        HubAdapter.getInstance().options
        verify(scopes).options
    }

    @Test fun `isCrashedLastRun calls Hub`() {
        HubAdapter.getInstance().isCrashedLastRun
        verify(scopes).isCrashedLastRun
    }

    @Test fun `reportFullyDisplayed calls Hub`() {
        HubAdapter.getInstance().reportFullyDisplayed()
        verify(scopes).reportFullyDisplayed()
    }
}
