package io.sentry.kotlin

import io.sentry.IHub
import io.sentry.Sentry
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

/**
 * Sentry context element for [CoroutineContext].
 */
public class SentryContext : ThreadContextElement<IHub>, AbstractCoroutineContextElement(Key) {

    private companion object Key : CoroutineContext.Key<SentryContext>

    private val hub: IHub = Sentry.getCurrentHub().clone()

    override fun updateThreadContext(context: CoroutineContext): IHub {
        val oldState = Sentry.getCurrentHub()
        Sentry.setCurrentHub(hub)
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: IHub) {
        Sentry.setCurrentHub(oldState)
    }
}
