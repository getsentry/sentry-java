package io.sentry.tests.nav3.r8

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import io.sentry.compose.navigation3.BackStackEntryMapper

/**
 * Maps every [Expectation] with `BackStackEntryMapper.SerialName`, and reports the results.
 *
 * An exception crashes the Activity, which the runner script reports as a failure.
 */
class BackStackEntryMapperActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Before the mapper's first map() call, which runs its compatibility probe.
    val logger = RecordingLogger()
    initSentry(this, logger)

    val nameMapper = BackStackEntryMapper.SerialName
    val expectations =
      listOf(
        Expectation("string", "home", null),
        Expectation("non-serializable class", NotSerializable("1"), null),
        Expectation("unannotated enum", UnannotatedEnum.A, null),
      ) + serializableExpectations()

    val lines = mutableListOf<String>()
    var failures = 0

    for (expectation in expectations) {
      val named = nameMapper.map(expectation.key)
      val matched = named?.name == expectation.expected && named?.arguments == null
      val fellBack = expectation.alsoAcceptsUnnamed && named == null
      val passed = matched || fellBack

      if (!passed) failures++

      lines +=
        "${if (passed) "PASS" else "FAIL"} ${expectation.label} " +
          "(runtime class ${expectation.key.javaClass.name}): " +
          "expected=${expectation.expected} actual=${named?.name}" +
          (named?.arguments?.let { " arguments=$it" } ?: "") +
          if (fellBack) " (INFO: fell back to /unknown, also accepted)" else ""
    }

    for (check in minificationChecks() + probeLogChecks(logger)) {
      if (!check.passed) failures++
      lines += "${if (check.passed) "PASS" else "FAIL"} ${check.description}"
    }

    lines += "RESULT=${if (failures == 0) "PASS" else "FAIL ($failures)"}"

    lines.forEach { Log.i(TAG, it) }
    setContentView(
      ScrollView(this).apply {
        addView(TextView(context).apply { text = lines.joinToString(separator = "\n") })
      },
    )
  }

  private companion object {
    const val TAG = "NAV3_R8_CHECK"
  }
}

/** A check of the build or the SDK's state, besides an [Expectation]. */
class Check(val description: String, val passed: Boolean)

/**
 * A back stack key and the name [BackStackEntryMapper.SerialName] should produce for it, or `null`
 * if it should be unnamed.
 *
 * If [alsoAcceptsUnnamed], the mapper may instead return `null` for the key in cases where neither
 * kotlinx.serialization nor R8 guarantees whether it can be named.
 */
class Expectation(
  val label: String,
  val key: Any,
  val expected: String?,
  val alsoAcceptsUnnamed: Boolean = false,
)

data class NotSerializable(val id: String)

enum class UnannotatedEnum {
  A,
}
