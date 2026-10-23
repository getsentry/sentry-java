package io.sentry.compose.navigation3.lifecycle

import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App

/**
 * The subset of the [ITransaction] interface used to record a nav transaction.
 *
 * Protects against accidental misuse of what would otherwise be a partially-implemented
 * `ITransaction` interface. New operations against the underlying `ITransaction` instance must be
 * added here. Operations not directly delegable to `ITransaction` should ***not*** be added.
 *
 * Sealed, as [NavTransactionHandle] is its only implementation.
 */
internal sealed interface INavTransaction {

  /** See [ITransaction.getStatus]. */
  val status: SpanStatus?

  /** See [ITransaction.setData]. */
  fun setData(key: String, value: Any)

  /** See [ITransaction.setContext]. */
  fun setContext(key: String, value: Any)

  /** Sets the transaction's app context. See [ITransaction.getContexts]. */
  fun setApp(app: App)

  /** See [ITransaction.finish]. */
  fun finish(status: SpanStatus)
}

/**
 * An [INavTransaction] backed by an [ITransaction] that's either started right away or, when the
 * nav lease isn't held, [started later][startDeferred].
 *
 * A deferred handle records what's set on it and replays it onto the transaction once started.
 * [Finishing][finish] a deferred handle before it starts discards the start.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. See [NavScopes].
 */
internal class NavTransactionHandle
private constructor(
  transaction: ITransaction?,
  private val deferredStart: DeferredStart?,
) : INavTransaction {

  /** The transaction backing this handle, or `null` if it hasn't started (or never will). */
  var transaction: ITransaction? = transaction
    private set

  private val data = linkedMapOf<String, Any>()
  private val contexts = linkedMapOf<String, Any>()
  private var app: App? = null
  private var isFinished = false

  /** Whether [startDeferred] can still start a transaction for this handle. */
  val isAwaitingStart: Boolean
    get() = transaction == null && deferredStart != null && !isFinished

  override val status: SpanStatus?
    get() = transaction?.status

  override fun setData(key: String, value: Any) {
    val transaction = transaction
    if (transaction == null) data[key] = value else transaction.setData(key, value)
  }

  override fun setContext(key: String, value: Any) {
    val transaction = transaction
    if (transaction == null) contexts[key] = value else transaction.setContext(key, value)
  }

  override fun setApp(app: App) {
    val transaction = transaction
    if (transaction == null) this.app = app else transaction.contexts.setApp(app)
  }

  /** Finishes the backing transaction or, if it hasn't started, discards the deferred start. */
  override fun finish(status: SpanStatus) {
    isFinished = true
    transaction?.finish(status)
  }

  /**
   * Starts the deferred transaction via [scopes] and replays what was set on this handle onto it.
   *
   * Returns the started transaction, or `null` if this handle [isn't awaiting a start][
   * isAwaitingStart] or [scopes] declined to start one.
   */
  fun startDeferred(scopes: IScopes): ITransaction? {
    val start = deferredStart?.takeIf { isAwaitingStart } ?: return null
    val started =
      scopes.startTransaction(start.context, start.options).takeUnless { it.isNoOp } ?: return null

    transaction = started
    data.forEach { (key, value) -> started.setData(key, value) }
    contexts.forEach { (key, value) -> started.setContext(key, value) }
    app?.let { started.contexts.setApp(it) }
    data.clear()
    contexts.clear()
    app = null
    return started
  }

  private class DeferredStart(val context: TransactionContext, val options: TransactionOptions)

  companion object {
    /** A handle backed by the already-started [transaction]. */
    fun started(transaction: ITransaction): NavTransactionHandle =
      NavTransactionHandle(transaction, deferredStart = null)

    /** A handle whose transaction is started later, via [startDeferred]. */
    fun deferred(context: TransactionContext, options: TransactionOptions): NavTransactionHandle =
      NavTransactionHandle(transaction = null, DeferredStart(context, options))
  }
}
