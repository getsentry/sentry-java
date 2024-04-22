package io.sentry

import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ScopesAdapterTest {

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
        ScopesAdapter.getInstance().isEnabled
        verify(scopes).isEnabled
    }

    @Test fun `captureEvent calls Hub`() {
        val event = mock<SentryEvent>()
        val hint = mock<Hint>()
        val scopeCallback = mock<ScopeCallback>()
        ScopesAdapter.getInstance().captureEvent(event, hint)
        verify(scopes).captureEvent(eq(event), eq(hint))

        ScopesAdapter.getInstance().captureEvent(event, hint, scopeCallback)
        verify(scopes).captureEvent(eq(event), eq(hint), eq(scopeCallback))
    }

    @Test fun `captureMessage calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        val sentryLevel = mock<SentryLevel>()
        ScopesAdapter.getInstance().captureMessage("message", sentryLevel)
        verify(scopes).captureMessage(eq("message"), eq(sentryLevel))

        ScopesAdapter.getInstance().captureMessage("message", sentryLevel, scopeCallback)
        verify(scopes).captureMessage(eq("message"), eq(sentryLevel), eq(scopeCallback))
    }

    @Test fun `captureEnvelope calls Hub`() {
        val envelope = mock<SentryEnvelope>()
        val hint = mock<Hint>()
        ScopesAdapter.getInstance().captureEnvelope(envelope, hint)
        verify(scopes).captureEnvelope(eq(envelope), eq(hint))
    }

    @Test fun `captureException calls Hub`() {
        val throwable = mock<Throwable>()
        val hint = mock<Hint>()
        val scopeCallback = mock<ScopeCallback>()
        ScopesAdapter.getInstance().captureException(throwable, hint)
        verify(scopes).captureException(eq(throwable), eq(hint))

        ScopesAdapter.getInstance().captureException(throwable, hint, scopeCallback)
        verify(scopes).captureException(eq(throwable), eq(hint), eq(scopeCallback))
    }

    @Test fun `captureUserFeedback calls Hub`() {
        val userFeedback = mock<UserFeedback>()
        ScopesAdapter.getInstance().captureUserFeedback(userFeedback)
        verify(scopes).captureUserFeedback(eq(userFeedback))
    }

    @Test fun `captureCheckIn calls Hub`() {
        val checkIn = mock<CheckIn>()
        ScopesAdapter.getInstance().captureCheckIn(checkIn)
        verify(scopes).captureCheckIn(eq(checkIn))
    }

    @Test fun `startSession calls Hub`() {
        ScopesAdapter.getInstance().startSession()
        verify(scopes).startSession()
    }

    @Test fun `endSession calls Hub`() {
        ScopesAdapter.getInstance().endSession()
        verify(scopes).endSession()
    }

    @Test fun `close calls Hub`() {
        ScopesAdapter.getInstance().close()
        verify(scopes).close(false)
    }

    @Test fun `close with isRestarting true calls Hub with isRestarting false`() {
        ScopesAdapter.getInstance().close(true)
        verify(scopes).close(false)
    }

    @Test fun `close with isRestarting false calls Hub with isRestarting false`() {
        ScopesAdapter.getInstance().close(false)
        verify(scopes).close(false)
    }

    @Test fun `addBreadcrumb calls Hub`() {
        val breadcrumb = mock<Breadcrumb>()
        val hint = mock<Hint>()
        ScopesAdapter.getInstance().addBreadcrumb(breadcrumb, hint)
        verify(scopes).addBreadcrumb(eq(breadcrumb), eq(hint))
    }

    @Test fun `setLevel calls Hub`() {
        val sentryLevel = mock<SentryLevel>()
        ScopesAdapter.getInstance().setLevel(sentryLevel)
        verify(scopes).setLevel(eq(sentryLevel))
    }

    @Test fun `setTransaction calls Hub`() {
        ScopesAdapter.getInstance().setTransaction("transaction")
        verify(scopes).setTransaction(eq("transaction"))
    }

    @Test fun `setUser calls Hub`() {
        val user = mock<User>()
        ScopesAdapter.getInstance().setUser(user)
        verify(scopes).setUser(eq(user))
    }

    @Test fun `setFingerprint calls Hub`() {
        val fingerprint = ArrayList<String>()
        ScopesAdapter.getInstance().setFingerprint(fingerprint)
        verify(scopes).setFingerprint(eq(fingerprint))
    }

    @Test fun `clearBreadcrumbs calls Hub`() {
        ScopesAdapter.getInstance().clearBreadcrumbs()
        verify(scopes).clearBreadcrumbs()
    }

    @Test fun `setTag calls Hub`() {
        ScopesAdapter.getInstance().setTag("key", "value")
        verify(scopes).setTag(eq("key"), eq("value"))
    }

    @Test fun `removeTag calls Hub`() {
        ScopesAdapter.getInstance().removeTag("key")
        verify(scopes).removeTag(eq("key"))
    }

    @Test fun `setExtra calls Hub`() {
        ScopesAdapter.getInstance().setExtra("key", "value")
        verify(scopes).setExtra(eq("key"), eq("value"))
    }

    @Test fun `removeExtra calls Hub`() {
        ScopesAdapter.getInstance().removeExtra("key")
        verify(scopes).removeExtra(eq("key"))
    }

    @Test fun `getLastEventId calls Hub`() {
        ScopesAdapter.getInstance().lastEventId
        verify(scopes).lastEventId
    }

    @Test fun `pushScope calls Hub`() {
        ScopesAdapter.getInstance().pushScope()
        verify(scopes).pushScope()
    }

    @Test fun `popScope calls Hub`() {
        ScopesAdapter.getInstance().popScope()
        verify(scopes).popScope()
    }

    @Test fun `withScope calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        ScopesAdapter.getInstance().withScope(scopeCallback)
        verify(scopes).withScope(eq(scopeCallback))
    }

    @Test fun `configureScope calls Hub`() {
        val scopeCallback = mock<ScopeCallback>()
        ScopesAdapter.getInstance().configureScope(scopeCallback)
        verify(scopes).configureScope(anyOrNull(), eq(scopeCallback))
    }

    @Test fun `bindClient calls Hub`() {
        val client = mock<ISentryClient>()
        ScopesAdapter.getInstance().bindClient(client)
        verify(scopes).bindClient(eq(client))
    }

    @Test fun `flush calls Hub`() {
        ScopesAdapter.getInstance().flush(1)
        verify(scopes).flush(eq(1))
    }

    @Test fun `clone calls Hub`() {
        ScopesAdapter.getInstance().clone()
        verify(scopes).clone()
    }

    @Test fun `captureTransaction calls Hub`() {
        val transaction = mock<SentryTransaction>()
        val traceContext = mock<TraceContext>()
        val hint = mock<Hint>()
        val profilingTraceData = mock<ProfilingTraceData>()
        ScopesAdapter.getInstance().captureTransaction(transaction, traceContext, hint, profilingTraceData)
        verify(scopes).captureTransaction(eq(transaction), eq(traceContext), eq(hint), eq(profilingTraceData))
    }

    @Test fun `startTransaction calls Hub`() {
        val transactionContext = mock<TransactionContext>()
        val samplingContext = mock<CustomSamplingContext>()
        val transactionOptions = mock<TransactionOptions>()
        ScopesAdapter.getInstance().startTransaction(transactionContext)
        verify(scopes).startTransaction(eq(transactionContext), any<TransactionOptions>())

        reset(scopes)

        ScopesAdapter.getInstance().startTransaction(transactionContext, transactionOptions)
        verify(scopes).startTransaction(eq(transactionContext), eq(transactionOptions))
    }

    @Test fun `traceHeaders calls Hub`() {
        ScopesAdapter.getInstance().traceHeaders()
        verify(scopes).traceHeaders()
    }

    @Test fun `setSpanContext calls Hub`() {
        val throwable = mock<Throwable>()
        val span = mock<ISpan>()
        ScopesAdapter.getInstance().setSpanContext(throwable, span, "transactionName")
        verify(scopes).setSpanContext(eq(throwable), eq(span), eq("transactionName"))
    }

    @Test fun `getSpan calls Hub`() {
        ScopesAdapter.getInstance().span
        verify(scopes).span
    }

    @Test fun `getTransaction calls Hub`() {
        ScopesAdapter.getInstance().transaction
        verify(scopes).transaction
    }

    @Test fun `getOptions calls Hub`() {
        ScopesAdapter.getInstance().options
        verify(scopes).options
    }

    @Test fun `isCrashedLastRun calls Hub`() {
        ScopesAdapter.getInstance().isCrashedLastRun
        verify(scopes).isCrashedLastRun
    }

    @Test fun `reportFullyDisplayed calls Hub`() {
        ScopesAdapter.getInstance().reportFullyDisplayed()
        verify(scopes).reportFullyDisplayed()
    }
}
