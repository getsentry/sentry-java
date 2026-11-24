package io.sentry.tests.nav3.r8

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry

/**
 * Maps every [Expectation] with `BackStackEntryMapper.SerialName` and
 * `BackStackEntryMapper.SerialNameAndProperties`, and reports the results.
 *
 * An exception crashes the Activity, which the runner script reports as a failure.
 */
class BackStackEntryMapperActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val nameMapper = BackStackEntryMapper.SerialName
    val propertiesMapper = BackStackEntryMapper.SerialNameAndProperties
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
      val withProperties = propertiesMapper.map(expectation.key)
      val matched =
        named?.name == expectation.expected &&
          named?.arguments == null &&
          withProperties?.name == expectation.expected &&
          withProperties?.arguments == expectation.expectedArguments
      val fellBack = expectation.alsoAcceptsUnnamed && named == null && withProperties == null
      val passed = matched || fellBack

      if (!passed) failures++

      lines +=
        "${if (passed) "PASS" else "FAIL"} ${expectation.label} " +
          "(runtime class ${expectation.key.javaClass.name}): " +
          "expected=${expectation.expected} actual=${named?.name}" +
          describeArguments(expectation, named?.arguments, withProperties) +
          if (fellBack) " (INFO: fell back to /unknown, also accepted)" else ""
    }

    for (check in minificationChecks() + integrationChecks()) {
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

  private fun describeArguments(
    expectation: Expectation,
    nameMapperArguments: Map<String, Any?>?,
    withProperties: SentryBackStackEntry?,
  ): String {
    val parts = mutableListOf<String>()

    if (nameMapperArguments != null) {
      parts += "SerialName arguments=$nameMapperArguments"
    }

    if (withProperties?.name != expectation.expected) {
      parts += "withProperties name=${withProperties?.name}"
    }

    if (expectation.expectedArguments != null || withProperties?.arguments != null) {
      parts +=
        "expectedArguments=${expectation.expectedArguments} " +
          "actualArguments=${withProperties?.arguments}"
    }

    return if (parts.isEmpty()) "" else " " + parts.joinToString(separator = " ")
  }

  /**
   * Checks that both mappers' compatibility probes passed under R8. A failed probe adds an
   * integration to the SDK info sent with every event (see `BuiltInBackStackEntryMappers` and
   * `KotlinxBackStackEntryMapper`). Runs after the mappers are created, i.e., after their probes.
   */
  private fun integrationChecks(): List<Check> {
    val integrations = SentryIntegrationPackageStorage.getInstance().integrations
    return listOf(
        "ComposeNavigation3SerialNameMappersUnavailable",
        "ComposeNavigation3PropertyEncodingDisabled",
      )
      .map { integration ->
        Check("$integration integration isn't added", integration !in integrations)
      }
  }

  private companion object {
    const val TAG = "NAV3_R8_CHECK"
  }
}

/** A check of the build or the SDK's state, besides an [Expectation]. */
class Check(val description: String, val passed: Boolean)

/**
 * A back stack key, the name [BackStackEntryMapper.SerialName] should produce for it, and the
 * arguments [BackStackEntryMapper.SerialNameAndProperties] should record for it.
 *
 * If [alsoAcceptsUnnamed], both mappers may instead return `null` for the key in cases where
 * neither kotlinx.serialization nor R8 guarantees whether it can be named.
 */
class Expectation(
  val label: String,
  val key: Any,
  val expected: String?,
  val expectedArguments: Map<String, Any?>? = null,
  val alsoAcceptsUnnamed: Boolean = false,
)

data class NotSerializable(val id: String)

enum class UnannotatedEnum {
  A,
}
