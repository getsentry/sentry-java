package io.sentry.kotlin

import io.sentry.IHub
import io.sentry.Sentry
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Sentry context element for [CoroutineContext].
 */
public class SentryContext(private val hub: IHub = Sentry.getCurrentHub().clone()) : CopyableThreadContextElement<IHub>, AbstractCoroutineContextElement(Key) {

    private companion object Key : CoroutineContext.Key<SentryContext>

    override fun copyForChild(): CopyableThreadContextElement<IHub> {
        return SentryContext(hub.clone())
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        return SentryContext(hub.clone())
    }

    override fun updateThreadContext(context: CoroutineContext): IHub {
        val oldState = Sentry.getCurrentHub()
        Sentry.setCurrentHub(hub)
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: IHub) {
        Sentry.setCurrentHub(oldState)
    }
}
