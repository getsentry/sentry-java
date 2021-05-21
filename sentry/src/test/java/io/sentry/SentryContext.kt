package io.sentry

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class SentryContext : ThreadContextElement<IHub>, AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<SentryContext>

    val hub: IHub = Sentry.getCurrentHub().clone()

    override fun updateThreadContext(context: CoroutineContext): IHub {
        val oldState = Sentry.getCurrentHub()
        Sentry.setCurrentHub(hub)
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: IHub) {
        Sentry.setCurrentHub(oldState)
    }
}
