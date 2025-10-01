package io.sentry

import io.sentry.Scope.IWithSession
import io.sentry.protocol.SentryId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class NoOpScopeTest {
  private var sut: NoOpScope = NoOpScope.getInstance()

  @Test
  fun `getLevel returns null`() {
    assertNull(sut.level)
  }

  @Test
  fun `getTransactionName returns null`() {
    assertNull(sut.transactionName)
  }

  @Test
  fun `getSpan returns null`() {
    assertNull(sut.span)
  }

  @Test
  fun `getUser returns null`() {
    assertNull(sut.user)
  }

  @Test
  fun `getScreen returns null`() {
    assertNull(sut.screen)
  }

  @Test
  fun `getRequest returns null`() {
    assertNull(sut.request)
  }

  @Test
  fun `getFingerprint returns empty list`() {
    assertEquals(0, sut.fingerprint.size)
  }

  @Test
  fun `getBreadcrumbs returns empty queue`() {
    assertEquals(0, sut.breadcrumbs.size)
  }

  @Test
  fun `getTransaction returns null`() {
    assertNull(sut.transaction)
  }

  @Test
  fun `getTags returns empty map`() {
    assertEquals(0, sut.tags.size)
  }

  @Test
  fun `getExtras returns empty map`() {
    assertEquals(0, sut.extras.size)
  }

  @Test
  fun `getContexts returns empty contexts`() {
    assertEquals(0, sut.contexts.size)
  }

  @Test
  fun `getAttachments returns empty list`() {
    assertEquals(0, sut.attachments.size)
  }

  @Test
  fun `getEventProcessors returns empty list`() {
    assertEquals(0, sut.eventProcessors.size)
  }

  @Test
  fun `withSession is NoOp`() {
    val withSessionCallback = mock<IWithSession>()
    sut.withSession(withSessionCallback)
    verify(withSessionCallback, never()).accept(any())
  }

  @Test
  fun `startSession returns null`() {
    assertNull(sut.startSession())
  }

  @Test
  fun `endSession returns null`() {
    assertNull(sut.endSession())
  }

  @Test
  fun `withTransaction is NoOp`() {
    val withTransactionCallback = mock<Scope.IWithTransaction>()
    sut.withTransaction(withTransactionCallback)
    verify(withTransactionCallback, never()).accept(any())
  }

  @Test
  fun `getSession returns null`() {
    assertNull(sut.session)
  }

  @Test
  fun `withPropagationContext is NoOp`() {
    val withPropagationContextCallback = mock<Scope.IWithPropagationContext>()
    sut.withPropagationContext(withPropagationContextCallback)
    verify(withPropagationContextCallback, never()).accept(any())
  }

  @Test fun `clone returns the same instance`() = assertSame(NoOpScope.getInstance(), sut.clone())

  @Test
  fun `getReplayId returns empty id`() {
    assertEquals(SentryId.EMPTY_ID, sut.replayId)
  }
}
