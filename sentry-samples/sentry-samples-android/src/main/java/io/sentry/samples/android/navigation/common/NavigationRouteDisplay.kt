package io.sentry.samples.android.navigation.common

import android.os.Bundle

internal fun RouteSpec.displayArguments(arguments: Bundle?): List<Pair<String, String>> =
  displayedArguments.mapNotNull { argument ->
    argument.toDisplayPair(arguments?.getString(argument.key))
  }

internal fun RouteSpec.displayArguments(arguments: Map<String, Any?>): List<Pair<String, String>> =
  displayedArguments.mapNotNull { argument ->
    argument.toDisplayPair(arguments[argument.key]?.toString())
  }

internal fun RouteSpec.displayRoute(arguments: Bundle?): String =
  displayRoute(displayArguments(arguments))

internal fun RouteSpec.displayRoute(arguments: Map<String, Any?>): String =
  displayRoute(displayArguments(arguments))

internal fun List<Pair<String, String>>.toDisplayString(): String =
  joinToString(", ") { (label, value) -> "$label=$value" }

private fun RouteSpec.displayRoute(displayArguments: List<Pair<String, String>>): String =
  if (displayArguments.isEmpty()) {
    "/$routeName"
  } else {
    "/$routeName { ${displayArguments.toDisplayString()} }"
  }

private fun DisplayedArgument.toDisplayPair(value: String?): Pair<String, String>? =
  value?.takeIf { it.isNotEmpty() }?.let { label to it }
