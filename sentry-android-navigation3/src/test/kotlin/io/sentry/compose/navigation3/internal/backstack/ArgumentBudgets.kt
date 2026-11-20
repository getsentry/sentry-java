package io.sentry.compose.navigation3.internal.backstack

import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES

/** Returns a budget with only [remaining] values left, as if other entries spent the rest. */
internal fun budgetWithValuesRemaining(remaining: Int): ArgumentBudget =
  ArgumentBudget().apply { repeat(MAX_ARGUMENT_VALUES - remaining) { spendValue() } }

/** Returns a budget with only [remaining] characters left, as if other entries spent the rest. */
internal fun budgetWithCharactersRemaining(remaining: Int): ArgumentBudget =
  ArgumentBudget().apply { spendText("x".repeat(MAX_ARGUMENT_CHARACTERS - remaining)) }
