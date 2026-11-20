package io.sentry.compose.navigation3.internal.backstack

/**
 * Arguments that write themselves with [write], like a built-in mapper's. Read as a map, they hold
 * only [entries], which differ from what they write unless a test sets them.
 */
internal class SelfWritingArguments(
  override val entries: Set<Map.Entry<String, Any?>> = mapOf("read" to "as a map").entries,
  private val write: (ArgumentSink) -> Unit,
) : AbstractMap<String, Any?>(), ArgumentSource {

  override fun writeTo(sink: ArgumentSink) {
    write(sink)
  }
}
