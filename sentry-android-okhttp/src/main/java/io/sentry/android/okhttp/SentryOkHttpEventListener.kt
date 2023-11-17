package io.sentry.android.okhttp

import io.sentry.HubAdapter
import io.sentry.IHub
import okhttp3.Call
import okhttp3.EventListener

/**
 *  Logs network performance event metrics to Sentry
 *
 *  Usage - add instance of [SentryOkHttpEventListener] in [okhttp3.OkHttpClient.Builder.eventListener]
 *
 * ```
 * val client = OkHttpClient.Builder()
 *     .eventListener(SentryOkHttpEventListener())
 *     .addInterceptor(SentryOkHttpInterceptor())
 *     .build()
 * ```
 *
 * If you already use a [okhttp3.EventListener], you can pass it in the constructor.
 *
 * ```
 * val client = OkHttpClient.Builder()
 *     .eventListener(SentryOkHttpEventListener(myEventListener))
 *     .addInterceptor(SentryOkHttpInterceptor())
 *     .build()
 * ```
 */
@Deprecated(
    "Use SentryOkHttpEventListener from sentry-okhttp instead",
    ReplaceWith("SentryOkHttpEventListener", "io.sentry.okhttp.SentryOkHttpEventListener")
)
class SentryOkHttpEventListener(
    hub: IHub = HubAdapter.getInstance(),
    originalEventListenerCreator: ((call: Call) -> EventListener)? = null
) : io.sentry.okhttp.SentryOkHttpEventListener(hub, originalEventListenerCreator) {
    constructor() : this(
        HubAdapter.getInstance(),
        originalEventListenerCreator = null
    )

    constructor(originalEventListener: EventListener) : this(
        HubAdapter.getInstance(),
        originalEventListenerCreator = { originalEventListener }
    )

    constructor(originalEventListenerFactory: Factory) : this(
        HubAdapter.getInstance(),
        originalEventListenerCreator = { originalEventListenerFactory.create(it) }
    )

    constructor(hub: IHub = HubAdapter.getInstance(), originalEventListener: EventListener) : this(
        hub,
        originalEventListenerCreator = { originalEventListener }
    )

    constructor(hub: IHub = HubAdapter.getInstance(), originalEventListenerFactory: Factory) : this(
        hub,
        originalEventListenerCreator = { originalEventListenerFactory.create(it) }
    )
}
