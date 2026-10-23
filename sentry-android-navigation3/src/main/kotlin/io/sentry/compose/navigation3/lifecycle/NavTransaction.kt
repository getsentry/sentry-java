package io.sentry.compose.navigation3.lifecycle

import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App

/**
 * An [INavTransaction] handle backed by an [ITransaction] that's either started right away or, when
 * the nav lease isn't held, [started later][start].
 *
 * A deferred handle records what's set on it and replays it onto the transaction once started.
 * [Finishing][finish] a deferred handle before it starts discards it, so its transaction never
 * starts.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Adhere to the thread safety policy from [NavScopes].
 */
internal class NavTransaction
private constructor(
  val name: String,
  private var state: State,
) : INavTransaction {

  /**
   * Whether the handle's transaction has yet to be [started][start], i.e., it's deferred and not
   * finished.
   */
  val isPending: Boolean
    get() = state is State.Pending

  /**
   * The backing transaction, once [started][State.Started]; `null` while [pending][State.Pending]
   * or once [discarded][State.Discarded].
   */
  val transaction: ITransaction?
    get() = (state as? State.Started)?.transaction

  override fun setData(key: String, value: Any) {
    when (val state = state) {
      is State.Pending -> state.data[key] = value
      is State.Started -> state.transaction.setData(key, value)
      State.Discarded -> Unit
    }
  }

  override fun setContext(key: String, value: Any) {
    when (val state = state) {
      is State.Pending -> state.contexts[key] = value
      is State.Started -> state.transaction.setContext(key, value)
      State.Discarded -> Unit
    }
  }

  override fun setApp(app: App) {
    when (val state = state) {
      is State.Pending -> state.app = app
      is State.Started -> state.transaction.contexts.setApp(app)
      State.Discarded -> Unit
    }
  }

  /**
   * Starts a deferred handle's transaction via [scopes] and replays what was set on the handle onto
   * it.
   *
   * Returns the started transaction, or `null` if the handle was started already or discarded, or
   * [scopes] declined to start one. A handle whose start is declined can be started again.
   */
  fun start(scopes: IScopes): ITransaction? {
    val pending = state as? State.Pending ?: return null
    val transaction =
      scopes.startTransaction(pending.context, pending.options).takeUnless { it.isNoOp }
        ?: return null

    state = State.Started(transaction)
    pending.data.forEach { (key, value) -> transaction.setData(key, value) }
    pending.contexts.forEach { (key, value) -> transaction.setContext(key, value) }
    pending.app?.let { transaction.contexts.setApp(it) }
    return transaction
  }

  /**
   * Finishes the backing transaction with its own status, or [OK][SpanStatus.OK] if it has none,
   * and then passes it to [onFinished].
   *
   * A deferred handle that hasn't started is discarded instead, so it never starts, and
   * [onFinished] isn't called.
   */
  fun finish(onFinished: (ITransaction) -> Unit) {
    when (val state = state) {
      is State.Pending -> this.state = State.Discarded
      is State.Started -> {
        if (!state.transaction.isFinished) {
          state.transaction.finish(state.transaction.status ?: SpanStatus.OK)
        }
        onFinished(state.transaction)
      }
      State.Discarded -> Unit
    }
  }

  /**
   * Lifecycle state for a [NavTransaction] handle.
   *
   * Starts in [Pending] state and terminates in either [Started] or [Discarded] state.
   */
  private sealed interface State {

    /**
     * Not started yet: the transaction will be started from [context] and [options], and what's set
     * on the handle until then is recorded for replay.
     */
    class Pending(val context: TransactionContext, val options: TransactionOptions) : State {
      val data = linkedMapOf<String, Any>()
      val contexts = linkedMapOf<String, Any>()
      var app: App? = null
    }

    /** The backing [transaction] has been started. A terminal [State]. */
    class Started(val transaction: ITransaction) : State

    /** The handle was finished before its backing transaction was started. A terminal [State]. */
    data object Discarded : State
  }

  companion object {
    /** A handle backed by the already-started [transaction]. */
    fun started(transaction: ITransaction): NavTransaction =
      NavTransaction(transaction.name, State.Started(transaction))

    /** A handle whose transaction is started later, via [start]. */
    fun deferred(context: TransactionContext, options: TransactionOptions): NavTransaction =
      NavTransaction(context.name, State.Pending(context, options))
  }
}

/**
 * The subset of the [ITransaction] interface used to record nav data on a nav transaction.
 *
 * Protects against accidental misuse of what would otherwise be a partially-implemented
 * `ITransaction` interface. New operations against the underlying `ITransaction` instance must be
 * added here. Operations not directly delegable to `ITransaction` should ***not*** be added.
 *
 * Starting and finishing a nav transaction are lease-aware, so they go through [INavScope] instead.
 */
internal sealed interface INavTransaction {

  /** See [ITransaction.setData]. */
  fun setData(key: String, value: Any)

  /** See [ITransaction.setContext]. */
  fun setContext(key: String, value: Any)

  /** Sets the transaction's app context. See [ITransaction.getContexts]. */
  fun setApp(app: App)
}
