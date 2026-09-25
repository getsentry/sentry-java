package io.sentry.uitest.android.mockservers

import io.sentry.SentryEnvelope
import io.sentry.SentryItemType
import org.msgpack.core.MessagePack

/** Key of the video part inside a `replay_video` envelope item. */
const val REPLAY_VIDEO_PART = "replay_video"

/**
 * Decodes the `replay_video` envelope item into its msgpack parts (`replay_event`,
 * `replay_recording` and [REPLAY_VIDEO_PART]), or returns null when the envelope holds no such
 * item.
 *
 * Relay discards a segment as `invalid_replay_video` when [REPLAY_VIDEO_PART] is absent or empty.
 * The SDK writes that part lazily, on the transport thread, so only the bytes that reach the server
 * show whether a segment is usable. A `SentryReplayEvent` seen in `beforeSendReplay` does not.
 */
fun SentryEnvelope.replayVideoParts(): Map<String, ByteArray>? {
  val item = items.firstOrNull { it.header.type == SentryItemType.ReplayVideo } ?: return null
  return unpackMsgpackMap(item.data)
}

private fun unpackMsgpackMap(bytes: ByteArray): Map<String, ByteArray> =
  MessagePack.newDefaultUnpacker(bytes).use { unpacker ->
    (0 until unpacker.unpackMapHeader()).associate {
      val key = unpacker.unpackString()
      key to unpacker.readPayload(unpacker.unpackBinaryHeader())
    }
  }
