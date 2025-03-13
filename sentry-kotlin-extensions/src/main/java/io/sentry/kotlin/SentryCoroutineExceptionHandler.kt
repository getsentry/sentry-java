package io.sentry.kotlin

import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryEvent
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import kotlinx.coroutines.CoroutineExceptionHandler
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ApiStatus.Experimental
public open class SentryCoroutineExceptionHandler(private val scopes: IScopes = ScopesAdapter.getInstance()) :
    AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        val mechanism = Mechanism().apply {
            type = "CoroutineExceptionHandler"
        }
        // the current thread is not necessarily the one that threw the exception
        val error = ExceptionMechanismException(mechanism, exception, Thread.currentThread())
        val event = SentryEvent(error)
        scopes.captureEvent(event)
    }
}
