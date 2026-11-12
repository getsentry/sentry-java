package io.sentry.android.core.anr

import com.google.common.truth.Truth.assertThat
import io.sentry.JsonObjectReader
import java.io.StringReader
import kotlin.test.Test

class AnrFlameGraphAttachmentTest {

  private val main = StackTraceElement("com.example.App", "main", "App.java", 10)
  private val work = StackTraceElement("com.example.App", "work", "App.java", 20)
  private val other = StackTraceElement("com.example.Other", "other", "Other.java", 30)

  @Test
  fun `aggregates samples into a caller-first call tree`() {
    val profile =
      AnrProfile(
        listOf(
          AnrStackTrace(1000, arrayOf(work, main)),
          AnrStackTrace(1066, arrayOf(work, main)),
          AnrStackTrace(1132, arrayOf(main)),
          AnrStackTrace(1198, arrayOf(main, main)),
          AnrStackTrace(1264, arrayOf(other)),
        )
      )

    val attachment = AnrFlameGraphAttachment.fromAnrProfile(profile, PROGUARD_UUID)!!

    assertThat(parse(String(attachment.bytes!!, Charsets.UTF_8)))
      .isEqualTo(
        parse(
          """
          {
            "version": "1",
            "platform": "java",
            "frames": [
              {"filename": "App.java", "function": "main", "module": "com.example.App", "lineno": 10},
              {"filename": "App.java", "function": "work", "module": "com.example.App", "lineno": 20},
              {"filename": "Other.java", "function": "other", "module": "com.example.Other", "lineno": 30}
            ],
            "nodes": [
              {"frame_id": 0, "sample_count": 4, "children": [1, 2]},
              {"frame_id": 1, "sample_count": 2},
              {"frame_id": 0, "sample_count": 1},
              {"frame_id": 2, "sample_count": 1}
            ],
            "trees": [
              {"roots": [0, 3], "thread_attributed": true}
            ],
            "debug_meta": {
              "images": [{"type": "proguard", "uuid": "$PROGUARD_UUID"}]
            }
          }
          """
        )
      )
  }

  @Test
  fun `marks native frames and omits unknown line numbers`() {
    val nativeFrame = StackTraceElement("java.lang.Object", "wait", null, -2)
    val profile = AnrProfile(listOf(AnrStackTrace(1000, arrayOf(nativeFrame))))

    val attachment = AnrFlameGraphAttachment.fromAnrProfile(profile, null)!!

    val flamegraph = parse(String(attachment.bytes!!, Charsets.UTF_8)) as Map<*, *>
    assertThat(flamegraph["frames"])
      .isEqualTo(
        listOf(mapOf("function" to "wait", "module" to "java.lang.Object", "native" to true))
      )
  }

  @Test
  fun `creates a flame graph attachment`() {
    val profile = AnrProfile(listOf(AnrStackTrace(1000, arrayOf(main))))

    val attachment = AnrFlameGraphAttachment.fromAnrProfile(profile, PROGUARD_UUID)!!

    assertThat(attachment.filename).isEqualTo("flamegraph.json")
    assertThat(attachment.contentType).isEqualTo("application/json")
    assertThat(attachment.attachmentType).isEqualTo("event.flamegraph")
  }

  @Test
  fun `omits debug meta without a proguard uuid`() {
    val profile = AnrProfile(listOf(AnrStackTrace(1000, arrayOf(main))))

    val attachment = AnrFlameGraphAttachment.fromAnrProfile(profile, null)!!

    val flamegraph = parse(String(attachment.bytes!!, Charsets.UTF_8)) as Map<*, *>
    assertThat(flamegraph).doesNotContainKey("debug_meta")
  }

  @Test
  fun `returns null when the profile has no samples`() {
    assertThat(AnrFlameGraphAttachment.fromAnrProfile(AnrProfile(emptyList()), PROGUARD_UUID))
      .isNull()
  }

  @Test
  fun `returns null when the profile has only empty stacks`() {
    val profile = AnrProfile(listOf(AnrStackTrace(1000, emptyArray())))

    assertThat(AnrFlameGraphAttachment.fromAnrProfile(profile, PROGUARD_UUID)).isNull()
  }

  @Test
  fun `returns null when the flame graph exceeds the size limit`() {
    val longClassName = "com.example." + "a".repeat(200)
    val stacks =
      (0 until 60).map { sample ->
        AnrStackTrace(
          1000L + sample,
          Array(1000) { depth ->
            StackTraceElement(longClassName, "method$sample-$depth", "A.java", depth + 1)
          },
        )
      }

    assertThat(AnrFlameGraphAttachment.fromAnrProfile(AnrProfile(stacks), PROGUARD_UUID)).isNull()
  }

  private companion object {
    const val PROGUARD_UUID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }

  private fun parse(json: String): Any? = JsonObjectReader(StringReader(json)).nextObjectOrNull()
}
