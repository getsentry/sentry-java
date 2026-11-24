package io.sentry.tests.nav3.r8

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import io.sentry.compose.navigation3.BackStackEntryMapper

/**
 * Checks that the built-in `BackStackEntryMapper`s fall back to `/unknown` (i.e., map entries to
 * `null`) instead of crashing in an app without kotlinx.serialization, and reports the results.
 *
 * An exception crashes the Activity, which the runner script reports as a failure.
 */
class BackStackEntryMapperActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val key = ProductKey("1")
    val checks =
      listOf(
        "kotlinx.serialization is absent" to !isKotlinxSerializationPresent(),
        "SerialName maps the entry to null" to (BackStackEntryMapper.SerialName.map(key) == null),
        "SerialNameAndProperties maps the entry to null" to
          (BackStackEntryMapper.SerialNameAndProperties.map(key) == null),
        // Shows the build was minified, so the absence of R8 build failures proves something.
        "ProductKey was renamed (runtime class ${ProductKey::class.java.name})" to
          (ProductKey::class.java.name != "io.sentry.tests.nav3.r8.ProductKey"),
      )

    val failures = checks.count { (_, passed) -> !passed }
    val lines =
      checks.map { (description, passed) -> "${if (passed) "PASS" else "FAIL"} $description" } +
        "RESULT=${if (failures == 0) "PASS" else "FAIL ($failures)"}"

    lines.forEach { Log.i(TAG, it) }
    setContentView(
      ScrollView(this).apply {
        addView(TextView(context).apply { text = lines.joinToString(separator = "\n") })
      },
    )
  }

  private fun isKotlinxSerializationPresent(): Boolean =
    try {
      Class.forName("kotlinx.serialization.KSerializer")
      true
    } catch (_: ClassNotFoundException) {
      false
    }

  private companion object {
    const val TAG = "NAV3_R8_CHECK"
  }
}

data class ProductKey(val id: String)
