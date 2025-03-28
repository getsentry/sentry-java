package io.sentry.samples.android

import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SpanLink
import io.sentry.TransactionOptions
import java.util.concurrent.atomic.AtomicReference

object Tracer {

  private val lastTrace = AtomicReference<ISpan>()

  @JvmStatic
  fun startSpan(opName: String): ISpan {
    val activeSpan = Sentry.getSpan()
    if (activeSpan != null) {
      return activeSpan.startChild(opName)
    }

    val root = Sentry.startTransaction(opName, opName, TransactionOptions().apply { isBindToScope = true })
    if (lastTrace.get() != null) {
      root.addLink(SpanLink(lastTrace.get().spanContext, mapOf("sentry.link.type" to "previous_trace")))
    }
    lastTrace.set(root)
    return root
  }

  @JvmStatic
  fun stopSpan(span: ISpan) {
    span.finish()
  }
}