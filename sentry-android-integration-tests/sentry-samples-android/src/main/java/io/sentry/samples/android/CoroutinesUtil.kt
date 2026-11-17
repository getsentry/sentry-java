package io.sentry.samples.android

import io.sentry.kotlin.SentryContext
import io.sentry.kotlin.SentryCoroutineExceptionHandler
import java.lang.RuntimeException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object CoroutinesUtil {
  fun throwInCoroutine() {
    GlobalScope.launch(SentryContext() + SentryCoroutineExceptionHandler()) {
      throw RuntimeException("Exception in coroutine")
    }
  }
}
