package io.sentry.samples.android

import io.sentry.kotlin.SentryCoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object CoroutinesUtil {

    fun throwCoroutines() {
        GlobalScope.launch(SentryCoroutineExceptionHandler()) {
            throw AssertionError()
        }
    }
}
