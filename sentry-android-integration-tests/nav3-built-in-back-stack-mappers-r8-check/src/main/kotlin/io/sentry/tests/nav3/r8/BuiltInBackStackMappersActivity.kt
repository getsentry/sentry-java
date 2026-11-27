package io.sentry.tests.nav3.r8

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import io.sentry.Sentry
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryNavEffect
import io.sentry.compose.navigation3.SentryNavOptions
import kotlinx.coroutines.delay

/**
 * Shows every [Expectation]'s key as one back stack to `SentryNavEffect` with
 * `BackStackEntryMapper.SerialName`, and reports the names and reasons it records.
 *
 * An exception crashes the Activity, which the runner script reports as a failure.
 */
class BuiltInBackStackMappersActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Before the mapper's first map() call, which runs its compatibility probe.
    val logger = RecordingLogger()
    val recorder = BackStackRecorder()
    initSentry(this, logger, recorder)

    val expectations =
      listOf(
        Expectation("string", "home", null, setOf(ENTRY_NOT_ANNOTATED)),
        Expectation(
          "non-serializable class",
          NotSerializable("1"),
          null,
          setOf(ENTRY_NOT_ANNOTATED),
        ),
        Expectation("unannotated enum", UnannotatedEnum.A, null, setOf(ENTRY_NOT_ANNOTATED)),
      ) + serializableExpectations()
    val backStack = expectations.map { it.key }
    val options = SentryNavOptions {
      enableNavigationTransactions = false
      maxCapturedBackStackEntries = backStack.size
    }

    setContent {
      SentryNavEffect(backStack, BackStackEntryMapper.SerialName, options)

      LaunchedEffect(Unit) {
        // The back stack reaches the scope once the effect holds the Activity's nav lease.
        var recorded: List<RecordedEntry>? = null
        for (attempt in 1..RECORD_ATTEMPTS) {
          recorded = recorder.recordedBackStack { Sentry.captureMessage(it) }
          if (recorded != null) break
          delay(RECORD_RETRY_MILLIS)
        }
        report(expectations, recorded?.reversed(), logger, recorder.lastEventSummary)
      }
    }
  }

  private fun report(
    expectations: List<Expectation>,
    recorded: List<RecordedEntry>?,
    logger: RecordingLogger,
    lastEventSummary: String,
  ) {
    val lines = mutableListOf<String>()
    var failures = 0

    if (recorded?.size != expectations.size) {
      failures++
      lines +=
        "FAIL SentryNavEffect recorded ${recorded?.size ?: "no"} back stack entries " +
          "(expected ${expectations.size}; $lastEventSummary)"
    } else {
      expectations.zip(recorded).forEach { (expectation, entry) ->
        val unnamed = entry.name == UNKNOWN
        val unnamedAsExpected =
          unnamed && entry.unknownEntryReason in expectation.unknownEntryReasons
        val passed =
          if (expectation.expected == null) {
            unnamedAsExpected
          } else {
            (entry.name == "/${expectation.expected}" && !entry.hasArguments) || unnamedAsExpected
          }
        val fellBack = expectation.expected != null && unnamedAsExpected

        if (!passed) failures++

        lines +=
          "${if (passed) "PASS" else "FAIL"} ${expectation.label} " +
            "(runtime class ${expectation.key.javaClass.name}): " +
            "expected=${expectation.expected?.let { "/$it" } ?: UNKNOWN} actual=${entry.name}" +
            (if (entry.hasArguments) " (has arguments)" else "") +
            (if (unnamed) {
              " reason=${entry.unknownEntryReason} (expected ${expectation.unknownEntryReasons})"
            } else {
              ""
            }) +
            if (fellBack) " (INFO: fell back to $UNKNOWN, also accepted)" else ""
      }
    }

    for (check in minificationChecks() + probeLogChecks(logger)) {
      if (!check.passed) failures++
      lines += "${if (check.passed) "PASS" else "FAIL"} ${check.description}"
    }

    lines += "RESULT=${if (failures == 0) "PASS" else "FAIL ($failures)"}"
    lines.forEach { Log.i(TAG, it) }
  }

  private companion object {
    const val TAG = "NAV3_R8_CHECK"
    const val UNKNOWN = "/unknown"
    const val RECORD_ATTEMPTS = 50
    const val RECORD_RETRY_MILLIS = 100L
  }
}

/** A check of the build or the SDK's state, besides an [Expectation]. */
class Check(val description: String, val passed: Boolean)

/**
 * A back stack key and the name `SentryNavEffect` should record for it with
 * [BackStackEntryMapper.SerialName], or `null` if it should be unnamed, with one of
 * [unknownEntryReasons].
 *
 * A key with an [expected] name may also have [unknownEntryReasons], if neither
 * kotlinx.serialization nor R8 guarantees whether it can be named. It may then instead be recorded
 * as `/unknown`, with one of those reasons.
 */
class Expectation(
  val label: String,
  val key: Any,
  val expected: String?,
  val unknownEntryReasons: Set<String> = emptySet(),
) {
  init {
    require(expected != null || unknownEntryReasons.isNotEmpty()) {
      "$label: an unnamed key must have the reasons it may be unnamed with."
    }
  }
}

data class NotSerializable(val id: String)

enum class UnannotatedEnum {
  A,
}
