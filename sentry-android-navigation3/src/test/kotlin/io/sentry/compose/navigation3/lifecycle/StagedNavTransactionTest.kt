package io.sentry.compose.navigation3.lifecycle

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.Scope
import io.sentry.ScopeObserverAdapter
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("LargeClass")
class StagedNavTransactionTest {

  private val transactionContext = TransactionContext("/Profile", "navigation")
  private val transactionOptions = TransactionOptions()

  private val finishedTransaction =
    mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }
  private val hostTransaction = mock<ITransaction>()

  @Test
  fun `start() - starts a nav transaction and binds it to a free scope`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction

    val navTransaction =
      stagedNavTransaction.start(fixture.scope, transactionContext, transactionOptions)

    val started = fixture.startedTransaction("/Profile")
    verify(fixture.scopes).startTransaction(transactionContext, transactionOptions)
    assertThat(navTransaction?.transaction).isSameInstanceAs(started)
    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
  }

  @Test
  fun `start() - starts a nav transaction without binding it while another transaction is bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = hostTransaction

    val navTransaction =
      stagedNavTransaction.start(fixture.scope, transactionContext, transactionOptions)

    val started = fixture.startedTransaction("/Profile")
    assertThat(navTransaction?.transaction).isSameInstanceAs(started)
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
    stagedNavTransaction.finish(fixture.scope)
    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `start() - binds the nav transaction in place of a finished bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = finishedTransaction

    val navTransaction =
      stagedNavTransaction.start(fixture.scope, transactionContext, transactionOptions)

    val started = fixture.startedTransaction("/Profile")
    assertThat(navTransaction?.transaction).isSameInstanceAs(started)
    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
  }

  @Test
  fun `start() - returns null and keeps the current nav transaction when the start is declined`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val current = fixture.start("/Home")
    fixture.declinesTransactions = true

    val navTransaction =
      stagedNavTransaction.start(fixture.scope, transactionContext, transactionOptions)

    assertThat(navTransaction).isNull()
    assertThat(fixture.scope.transaction).isSameInstanceAs(current)
    stagedNavTransaction.finish(fixture.scope)
    verify(current).finish(SpanStatus.OK)
  }

  @Test
  fun `start() - replaces the current nav transaction without finishing it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val current = fixture.start("/Home")

    stagedNavTransaction.start(fixture.scope, transactionContext, transactionOptions)
    verify(current, never()).finish(any())

    stagedNavTransaction.finish(fixture.scope)
    verify(fixture.startedTransaction("/Profile")).finish(SpanStatus.OK)
    verify(current, never()).finish(any())
  }

  @Test
  fun `start() - keeps the started nav transaction when binding it throws`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.transactionObserverThrows = true

    assertThrows(ThrowingScopeObserverException::class.java) {
      stagedNavTransaction.start(fixture.scope, transactionContext, transactionOptions)
    }
    fixture.transactionObserverThrows = false
    val started = fixture.startedTransaction("/Profile")
    stagedNavTransaction.finish(fixture.scope)

    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `stage() - returns a pending nav transaction without starting a transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction

    val navTransaction = stagedNavTransaction.stage(transactionContext, transactionOptions)

    assertThat(navTransaction.transaction).isNull()
    assertThat(navTransaction.name).isEqualTo("/Profile")
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `stage() - replaces the current nav transaction without finishing it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val current = fixture.start("/Home")

    stagedNavTransaction.stage(transactionContext, transactionOptions)
    stagedNavTransaction.finish(fixture.scope)

    verify(current, never()).finish(any())
  }

  @Test
  fun `publishTo() - starts a staged nav transaction and binds it to a free scope`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val navTransaction = stagedNavTransaction.stage(transactionContext, transactionOptions)

    stagedNavTransaction.publishTo(fixture.scope)

    val started = fixture.startedTransaction("/Profile")
    assertThat(navTransaction.transaction).isSameInstanceAs(started)
    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
  }

  @Test
  fun `publishTo() - starts a staged nav transaction in place of a finished bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(transactionContext, transactionOptions)
    fixture.scope.transaction = finishedTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `publishTo() - doesn't start a staged nav transaction while another transaction is bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(transactionContext, transactionOptions)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `publishTo() - logs the route when another transaction blocks the nav transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(transactionContext, transactionOptions)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    verify(fixture.logger).log(eq(SentryLevel.DEBUG), any<String>(), eq("/Profile"))
  }

  @Test
  fun `publishTo() - discards a staged nav transaction blocked by another transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val navTransaction = stagedNavTransaction.stage(transactionContext, transactionOptions)
    fixture.scope.transaction = hostTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(navTransaction.start(fixture.scopes)).isNull()
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `publishTo() - binds nothing when a staged nav transaction's start is declined`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(transactionContext, transactionOptions)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `publishTo() - discards a staged nav transaction whose start was declined`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(transactionContext, transactionOptions)
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.declinesTransactions = false

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `publishTo() - binds an unpublished unfinished nav transaction again`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    assertThat(fixture.startedTransactions).containsExactly(started)
  }

  @Test
  fun `publishTo() - keeps the bound nav transaction bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    verify(fixture.logger, never()).log(eq(SentryLevel.DEBUG), any<String>(), any<Any>())
  }

  @Test
  fun `publishTo() - binds an unpublished nav transaction in place of a finished bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = finishedTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
  }

  @Test
  fun `publishTo() - doesn't bind an unpublished nav transaction while another transaction is bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
    stagedNavTransaction.finish(fixture.scope)
    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `publishTo() - forgets and unbinds a bound nav transaction that has finished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    whenever(started.isFinished).thenReturn(true)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `publishTo() - forgets an unpublished nav transaction that has finished and keeps another transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    whenever(started.isFinished).thenReturn(true)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `publishTo() - leaves the scope untouched without a nav transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = finishedTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(finishedTransaction)
  }

  @Test
  fun `unpublishFrom() - unbinds the started nav transaction without finishing it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.unpublishFrom(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
    verify(started, never()).finish(any())
  }

  @Test
  fun `unpublishFrom() - keeps another bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = hostTransaction
    fixture.start()

    stagedNavTransaction.unpublishFrom(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `unpublishFrom() - keeps the bound transaction while the nav transaction is staged`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val navTransaction = stagedNavTransaction.stage(transactionContext, transactionOptions)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.unpublishFrom(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
    assertThat(navTransaction.transaction).isNull()
  }

  @Test
  fun `finish() - finishes the started nav transaction and unbinds it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.finish(fixture.scope)

    verify(started).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finish() - finishes an unpublished nav transaction and keeps another bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.finish(fixture.scope)

    verify(started).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `finish() - forgets the nav transaction so that publishing never binds it again`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.start()
    stagedNavTransaction.finish(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finish() - discards a staged nav transaction so that publishing never starts it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val navTransaction = stagedNavTransaction.stage(transactionContext, transactionOptions)
    stagedNavTransaction.finish(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(navTransaction.start(fixture.scopes)).isNull()
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finish() - forgets the nav transaction when unbinding it throws`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    fixture.transactionObserverThrows = true

    assertThrows(ThrowingScopeObserverException::class.java) {
      stagedNavTransaction.finish(fixture.scope)
    }
    stagedNavTransaction.finish(fixture.scope)

    // Finished once: the second call has no nav transaction left to finish or unbind.
    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `finish() - leaves the scope untouched without a nav transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.finish(fixture.scope)

    verify(hostTransaction, never()).finish(any())
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `forget() - forgets the started nav transaction without finishing or unbinding it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.forget()
    stagedNavTransaction.finish(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    verify(started, never()).finish(any())
  }

  @Test
  fun `forget() - forgets a staged nav transaction so that publishing never starts it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(transactionContext, transactionOptions)
    stagedNavTransaction.forget()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  /** Thrown by a scope observer, to stand in for one the host app registered. */
  private class ThrowingScopeObserverException : RuntimeException()

  private class Fixture private constructor() {
    val scope = Scope(SentryOptions())
    val logger = mock<ILogger>()

    /** Whether [scopes] declines to start transactions, returning a no-op transaction instead. */
    var declinesTransactions = false
    val startedTransactions = mutableListOf<ITransaction>()

    /** Whether a scope observer throws when the scope's transaction is bound or unbound. */
    var transactionObserverThrows = false

    val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.options)
          .thenReturn(
            SentryOptions().apply {
              setLogger(this@Fixture.logger)
              isDebug = true
            }
          )
        whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
          .thenAnswer {
            if (declinesTransactions) {
              NoOpTransaction.getInstance()
            } else {
              val name = (it.arguments[0] as TransactionContext).name
              mock<ITransaction>()
                .also { transaction -> whenever(transaction.name).thenReturn(name) }
                .also(startedTransactions::add)
            }
          }
      }

    val stagedNavTransaction = StagedNavTransaction(scopes)

    init {
      scope.options.addScopeObserver(
        object : ScopeObserverAdapter() {
          override fun setTransaction(transaction: String?) {
            if (transactionObserverThrows) {
              throw ThrowingScopeObserverException()
            }
          }
        }
      )
    }

    fun startedTransaction(name: String): ITransaction = startedTransactions.single {
      it.name == name
    }

    /** Starts a nav transaction for [name], binding it if the scope is free, and returns it. */
    fun start(name: String = "/Profile"): ITransaction {
      stagedNavTransaction.start(
        scope,
        TransactionContext(name, "navigation"),
        TransactionOptions(),
      )
      return startedTransaction(name)
    }

    companion object {
      fun create(declinesTransactions: Boolean = false): Fixture =
        Fixture().also { it.declinesTransactions = declinesTransactions }
    }
  }
}
