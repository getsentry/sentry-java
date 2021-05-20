package io.sentry

import kotlinx.coroutines.ThreadContextElement
import org.slf4j.LoggerFactory
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class SentryContext : ThreadContextElement<IHub>, AbstractCoroutineContextElement(Key) {
    val logger = LoggerFactory.getLogger(SentryContext::class.java)

    companion object Key : CoroutineContext.Key<SentryContext>

    val hub: IHub = Sentry.getCurrentHub().clone()

    override fun updateThreadContext(context: CoroutineContext): IHub {
        logger.error("Updating thread context")
        val oldState = Sentry.getCurrentHub().clone()
        tags("old state", oldState)
        Sentry.setCurrentHub(hub)
        tags("set current hub", oldState)
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: IHub) {
        logger.error("Restoring thread context")
        tags("set current hub", oldState)
        Sentry.setCurrentHub(oldState)
    }

    fun tags(prefix: String, hub: IHub) {
        var result:Map<String, String>? = null
        hub.configureScope {
            result= it.tags
        }
        logger.error("$prefix, hub: $hub, tags: $result")
    }

}
