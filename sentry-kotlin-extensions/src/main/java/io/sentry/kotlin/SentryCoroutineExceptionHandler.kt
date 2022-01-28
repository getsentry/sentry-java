package io.sentry.kotlin

import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import kotlinx.coroutines.CoroutineExceptionHandler
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Sentry exception handler element for [CoroutineExceptionHandler].
 */
@ApiStatus.Experimental
public class SentryCoroutineExceptionHandler(private val hub: IHub = HubAdapter.getInstance()) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
    public constructor() : this(HubAdapter.getInstance())

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        val mechanism = Mechanism().apply {
            type = "CoroutineExceptionHandler"
        }
        // [CoroutineExceptionHandler] can be invoked from an arbitrary thread
        val error = ExceptionMechanismException(mechanism, exception, Thread.currentThread())
        val event = SentryEvent(error)
        hub.captureEvent(event)
    }
}
