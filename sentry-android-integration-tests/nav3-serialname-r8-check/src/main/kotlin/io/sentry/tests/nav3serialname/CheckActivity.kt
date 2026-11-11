package io.sentry.tests.nav3serialname

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry

/**
 * A back stack key, the name [BackStackEntryMapper.serialName] should produce for it, and the
 * arguments [BackStackEntryMapper.serialNameWithArguments] should record for it.
 */
class Expectation(
  val label: String,
  val key: Any,
  val expected: String?,
  val expectedArguments: Map<String, Any?>? = null,
)

data class NotSerializable(val id: String)

enum class UnannotatedEnum {
  A
}

/**
 * Maps every [Expectation] with [BackStackEntryMapper.serialName] and
 * [BackStackEntryMapper.serialNameWithArguments], and reports the results. An exception crashes the
 * app, which the runner script reports as a failure.
 */
class CheckActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val nameMapper = BackStackEntryMapper.serialName()
    val argumentsMapper = BackStackEntryMapper.serialNameWithArguments()
    val expectations =
      listOf(
        Expectation("string", "home", null),
        Expectation("non-serializable class", NotSerializable("1"), null),
        Expectation("unannotated enum", UnannotatedEnum.A, null),
      ) + flavorExpectations()

    val lines = mutableListOf<String>()
    var failures = 0
    for (expectation in expectations) {
      val named = nameMapper.map(expectation.key)
      val withArguments = argumentsMapper.map(expectation.key)
      val passed =
        named?.name == expectation.expected &&
          named?.arguments == null &&
          withArguments?.name == expectation.expected &&
          withArguments?.arguments == expectation.expectedArguments
      if (!passed) failures++
      lines +=
        "${if (passed) "PASS" else "FAIL"} ${expectation.label} " +
          "(runtime class ${expectation.key.javaClass.name}): " +
          "expected=${expectation.expected} actual=${named?.name}" +
          describeArguments(expectation, named?.arguments, withArguments)
    }
    for (check in flavorMinificationChecks()) {
      if (!check.passed) failures++
      lines += "${if (check.passed) "PASS" else "FAIL"} ${check.description}"
    }
    lines += "FLAVOR=$FLAVOR RESULT=${if (failures == 0) "PASS" else "FAIL ($failures)"}"

    lines.forEach { Log.i(TAG, it) }
    setContentView(
      ScrollView(this).apply {
        addView(TextView(context).apply { text = lines.joinToString(separator = "\n") })
      }
    )
  }

  private fun describeArguments(
    expectation: Expectation,
    nameMapperArguments: Map<String, Any?>?,
    withArguments: SentryBackStackEntry?,
  ): String {
    val parts = mutableListOf<String>()
    if (nameMapperArguments != null) {
      parts += "serialName() arguments=$nameMapperArguments"
    }
    if (withArguments?.name != expectation.expected) {
      parts += "withArguments name=${withArguments?.name}"
    }
    if (expectation.expectedArguments != null || withArguments?.arguments != null) {
      parts +=
        "expectedArguments=${expectation.expectedArguments} " +
          "actualArguments=${withArguments?.arguments}"
    }
    return if (parts.isEmpty()) "" else " " + parts.joinToString(separator = " ")
  }

  private companion object {
    const val TAG = "NAV3_R8_CHECK"
  }
}

/** A check that the build was minified, so passing names prove something. */
class MinificationCheck(val description: String, val passed: Boolean)
