package io.sentry.samples.android

import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the single global beforeSendTransaction hook used by sample screens that inspect finished
 * transactions.
 *
 * Multiple sample activities can overlap briefly during configuration changes or relaunches. If
 * each screen installs and restores its own callback, a later uninstall can resurrect a stale
 * wrapper callback that still retains dead state and drops the original callback chain.
 *
 * Keep one stable callback installed and let sample screens register lightweight listeners against
 * it instead.
 */
internal object SampleBeforeSendTransactionHook {

  private val listeners = CopyOnWriteArrayList<(SentryTransaction, String?) -> Unit>()

  @Volatile private var installedCallback: SentryOptions.BeforeSendTransactionCallback? = null
  @Volatile private var previousCallback: SentryOptions.BeforeSendTransactionCallback? = null

  fun installIfNeeded(options: SentryOptions) {
    if (installedCallback != null) {
      return
    }

    synchronized(this) {
      if (installedCallback != null) {
        return
      }

      previousCallback = options.beforeSendTransaction
      val callback = SentryOptions.BeforeSendTransactionCallback { transaction, hint ->
        val previous = previousCallback

        val processedTransaction =
          if (previous == null) {
            transaction
          } else {
            previous.execute(transaction, hint)
          }

        processedTransaction?.let { processed ->
          listeners.forEach { listener ->
            try {
              listener(processed, options.dsn)
            } catch (e: RuntimeException) {
              options.logger.log(
                io.sentry.SentryLevel.ERROR,
                "Sample transaction listener failed.",
                e,
              )
            }
          }
        }

        processedTransaction
      }

      installedCallback = callback
      options.beforeSendTransaction = callback
    }
  }

  fun addListener(listener: (SentryTransaction, String?) -> Unit) {
    listeners.addIfAbsent(listener)
  }

  fun removeListener(listener: (SentryTransaction, String?) -> Unit) {
    listeners.remove(listener)
  }
}
