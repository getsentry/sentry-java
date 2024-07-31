// ktlint-disable filename
package io.sentry.android.replay.util

import io.sentry.ReplayRecording
import io.sentry.SentryOptions
import io.sentry.android.replay.ReplayCache
import io.sentry.rrweb.RRWebEvent
import java.io.BufferedWriter
import java.io.StringWriter
import java.util.LinkedList
import java.util.concurrent.ScheduledExecutorService

internal class PersistableLinkedList(
    private val propertyName: String,
    private val options: SentryOptions,
    private val persistingExecutor: ScheduledExecutorService,
    private val cacheProvider: () -> ReplayCache?
) : LinkedList<RRWebEvent>() {
    // only overriding methods that we use, to observe the collection
    override fun addAll(elements: Collection<RRWebEvent>): Boolean {
        val result = super.addAll(elements)
        persistRecording()
        return result
    }

    override fun add(element: RRWebEvent): Boolean {
        val result = super.add(element)
        persistRecording()
        return result
    }

    override fun remove(): RRWebEvent {
        val result = super.remove()
        persistRecording()
        return result
    }

    private fun persistRecording() {
        val cache = cacheProvider() ?: return
        val recording = ReplayRecording().apply { payload = ArrayList(this@PersistableLinkedList) }
        if (options.mainThreadChecker.isMainThread) {
            persistingExecutor.submit {
                val stringWriter = StringWriter()
                options.serializer.serialize(recording, BufferedWriter(stringWriter))
                cache.persistSegmentValues(propertyName, stringWriter.toString())
            }
        } else {
            val stringWriter = StringWriter()
            options.serializer.serialize(recording, BufferedWriter(stringWriter))
            cache.persistSegmentValues(propertyName, stringWriter.toString())
        }
    }
}
