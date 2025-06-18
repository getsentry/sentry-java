package io.sentry.uitest.android.utils

import androidx.test.espresso.IdlingResource
import java.util.concurrent.atomic.AtomicBoolean

/** Idling resource based on a boolean flag. */
class BooleanIdlingResource(
    private val name: String,
) : IdlingResource {
    private val isIdle = AtomicBoolean(true)

    private val isIdleLock = Object()

    private var callback: IdlingResource.ResourceCallback? = null

    /** Sets whether this resource is currently in idle state. */
    fun setIdle(idling: Boolean) {
        if (!isIdle.getAndSet(idling) && idling) {
            callback?.onTransitionToIdle()
        }
    }

    override fun getName(): String = name

    /** Check if this resource is currently in idle state. */
    override fun isIdleNow(): Boolean = synchronized(isIdleLock) { isIdle.get() }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
    }
}
