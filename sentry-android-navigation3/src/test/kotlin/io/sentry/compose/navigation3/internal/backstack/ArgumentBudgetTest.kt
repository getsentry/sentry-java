package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test

class ArgumentBudgetTest {

  // spendText(CharSequence)

  @Test
  fun `spendText(CharSequence) - returns text exactly as long as the remaining characters as a String`() {
    val budget = budgetWithCharactersRemaining(2)

    val spent = budget.spendText(StringBuilder("ab"))

    assertThat(spent).isEqualTo("ab")
  }

  @Test
  fun `spendText(CharSequence) - spends the characters of the text`() {
    val budget = budgetWithCharactersRemaining(2)
    budget.spendText(StringBuilder("ab"))

    val drop = assertFailsWith<DropArguments> { budget.spendText(StringBuilder("c")) }

    assertThat(drop.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(drop.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
  }

  @Test
  fun `spendText(CharSequence) - spends the characters of the text rather than its reported length`() {
    val budget = budgetWithCharactersRemaining(2)
    budget.spendText(CountingText(length = 0, text = "ab"))

    val drop = assertFailsWith<DropArguments> { budget.spendText(StringBuilder("c")) }

    assertThat(drop.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `spendText(CharSequence) - throws for the character limit without converting text that's too long`() {
    val budget = budgetWithCharactersRemaining(1)
    val text = CountingText(length = 2, text = "ab")

    val drop = assertFailsWith<DropArguments> { budget.spendText(text) }

    assertThat(drop.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(drop.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
    assertThat(text.toStringCalls).isEqualTo(0)
  }

  @Test
  fun `spendText(CharSequence) - throws for the character limit when text is longer than its reported length`() {
    val budget = budgetWithCharactersRemaining(1)

    val drop =
      assertFailsWith<DropArguments> { budget.spendText(CountingText(length = 1, text = "ab")) }

    assertThat(drop.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(drop.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
  }
}
