package io.sentry.tests.nav3serialname

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import io.sentry.compose.navigation3.BackStackEntryMapper

/** A back stack key and the name [BackStackEntryMapper.serialName] should produce for it. */
class Expectation(val label: String, val key: Any, val expected: String?)

data class NotSerializable(val id: String)

enum class UnannotatedEnum {
  A
}

/**
 * Maps every [Expectation] with [BackStackEntryMapper.serialName] and reports the results. An
 * exception crashes the app, which the runner script reports as a failure.
 */
class CheckActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val mapper = BackStackEntryMapper.serialName()
    val expectations =
      listOf(
        Expectation("string", "home", null),
        Expectation("non-serializable class", NotSerializable("1"), null),
        Expectation("unannotated enum", UnannotatedEnum.A, null),
      ) + flavorExpectations()

    val lines = mutableListOf<String>()
    var failures = 0
    for (expectation in expectations) {
      val actual = mapper.map(expectation.key)?.name
      val passed = actual == expectation.expected
      if (!passed) failures++
      lines +=
        "${if (passed) "PASS" else "FAIL"} ${expectation.label} " +
          "(runtime class ${expectation.key.javaClass.name}): " +
          "expected=${expectation.expected} actual=$actual"
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

  private companion object {
    const val TAG = "NAV3_R8_CHECK"
  }
}

/** A check that the build was minified, so passing names prove something. */
class MinificationCheck(val description: String, val passed: Boolean)
