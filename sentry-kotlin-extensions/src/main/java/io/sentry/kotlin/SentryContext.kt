package io.sentry.kotlin

import io.sentry.IScopes
import io.sentry.Sentry
import kotlinx.coroutines.CopyableThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Sentry context element for [CoroutineContext].
 */
public class SentryContext(
    private val scopes: IScopes = Sentry.forkedCurrentScope("coroutine"),
) : AbstractCoroutineContextElement(Key),
    CopyableThreadContextElement<IScopes> {
    private companion object Key : CoroutineContext.Key<SentryContext>

    @SuppressWarnings("deprecation")
    override fun copyForChild(): CopyableThreadContextElement<IScopes> = SentryContext(scopes.forkedCurrentScope("coroutine.child"))

    @SuppressWarnings("deprecation")
    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        overwritingElement[Key] ?: SentryContext(scopes.forkedCurrentScope("coroutine.child"))

    override fun updateThreadContext(context: CoroutineContext): IScopes {
        val oldState = Sentry.getCurrentScopes()
        Sentry.setCurrentScopes(scopes)
        return oldState
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: IScopes,
    ) {
        Sentry.setCurrentScopes(oldState)
    }
}
