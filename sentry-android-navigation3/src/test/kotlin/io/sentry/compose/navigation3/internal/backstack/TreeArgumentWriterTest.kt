package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.util.UrlUtils
import kotlin.test.assertFailsWith
import org.junit.Test

class TreeArgumentWriterTest {

  private val budget = ArgumentBudget()
  private val sink = TreeArgumentWriter(budget)

  private val characterLimitDrop =
    ArgumentDropReason.CHARACTER_LIMIT to DropArguments.MAX_CHARACTER_WARNING
  private val duplicateKeyDrop =
    ArgumentDropReason.DUPLICATE_KEY to DropArguments.DUPLICATE_KEY_WARNING
  private val maxCountDrop = ArgumentDropReason.MAX_COUNT to DropArguments.MAX_COUNT_WARNING
  private val structureDrop =
    ArgumentDropReason.INVALID_STRUCTURE to DropArguments.STRUCTURE_WARNING

  private val DropArguments.signal: Pair<ArgumentDropReason?, String?>
    get() = reason to warning

  private companion object {
    const val SUBSTITUTE = UrlUtils.SENSITIVE_DATA_SUBSTITUTE
  }

  // Building the tree

  @Test
  fun `argumentsOrThrow() - returns the arguments written as maps and lists`() {
    sink.key("id")
    sink.scalar("123")
    sink.key("filters")
    sink.beginMap()
    sink.key("tags")
    sink.beginList()
    sink.scalar("a")
    sink.scalar(true)
    sink.end()
    sink.key("limit")
    sink.scalar(null)
    sink.end()
    sink.key("page")
    sink.scalar(2)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments)
      .isEqualTo(
        mapOf(
          "id" to "123",
          "filters" to mapOf("tags" to listOf("a", true), "limit" to null),
          "page" to 2,
        ),
      )
  }

  @Test
  fun `argumentsOrThrow() - keeps arguments in the order they were written`() {
    sink.key("b")
    sink.scalar(1)
    sink.key("a")
    sink.scalar(2)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments.keys).containsExactly("b", "a").inOrder()
  }

  @Test
  fun `argumentsOrThrow() - returns an empty map when nothing was written`() {
    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEmpty()
  }

  // Scalars

  @Test
  fun `scalar() - records CharSequence and Char values as Strings`() {
    sink.key("text")
    sink.scalar(StringBuilder("hello"))
    sink.key("char")
    sink.scalar('x')

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("text" to "hello", "char" to "x"))
    assertThat(arguments["text"]).isInstanceOf(String::class.java)
    assertThat(arguments["char"]).isInstanceOf(String::class.java)
  }

  @Test
  fun `scalar() - writes text exactly as long as the remaining characters`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(2))
    limitedSink.key("")

    limitedSink.scalar("ab")

    assertThat(limitedSink.argumentsOrThrow()).isEqualTo(mapOf("" to "ab"))
  }

  @Test
  fun `scalar() - spends characters for a CharSequence value`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(1))
    limitedSink.key("")

    val drop = assertFailsWith<DropArguments> { limitedSink.scalar(StringBuilder("ab")) }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
  }

  @Test
  fun `scalar() - drops for the character limit without converting a CharSequence that's too long`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(1))
    limitedSink.key("")
    val text = CountingText(length = 2, text = "ab")

    val drop = assertFailsWith<DropArguments> { limitedSink.scalar(text) }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
    assertThat(text.toStringCalls).isEqualTo(0)
  }

  @Test
  fun `scalar() - drops for the character limit when a CharSequence is longer than its reported length`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(1))
    limitedSink.key("")

    val drop =
      assertFailsWith<DropArguments> { limitedSink.scalar(CountingText(length = 1, text = "ab")) }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
  }

  @Test
  fun `scalar() - propagates the exception a CharSequence's toString() throws`() {
    val error = IllegalArgumentException("unreadable")
    sink.key("text")

    val thrown = assertFailsWith<IllegalArgumentException> { sink.scalar(FailingText(error)) }

    assertThat(thrown).isSameInstanceAs(error)
    assertThat(sink.failure).isNull()
  }

  @Test
  fun `scalar() - adds nothing for a CharSequence whose toString() throws, so its key can take a value`() {
    sink.key("text")
    assertFailsWith<IllegalArgumentException> {
      sink.scalar(FailingText(IllegalArgumentException("unreadable")))
    }

    sink.scalar("replacement")

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf("text" to "replacement"))
  }

  @Test
  fun `scalar() - spends characters for a Char value`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(0))
    limitedSink.key("")

    val drop = assertFailsWith<DropArguments> { limitedSink.scalar('x') }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
  }

  @Test
  fun `scalar() - records non-finite Double and Float values as strings`() {
    sink.key("double")
    sink.scalar(Double.NEGATIVE_INFINITY)
    sink.key("float")
    sink.scalar(Float.NaN)

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("double" to "-Infinity", "float" to "NaN"))
  }

  @Test
  fun `scalar() - spends characters for a non-finite number string`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining("NaN".length - 1))
    limitedSink.key("")

    val drop = assertFailsWith<DropArguments> { limitedSink.scalar(Double.NaN) }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
  }

  @Test
  fun `scalar() - doesn't spend characters for a finite number`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(0))
    limitedSink.key("")

    limitedSink.scalar(123.456)

    assertThat(limitedSink.argumentsOrThrow()).isEqualTo(mapOf("" to 123.456))
  }

  @Test
  fun `scalar() - doesn't spend characters for a boolean or null`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(0))
    limitedSink.key("")
    limitedSink.beginList()

    limitedSink.scalar(true)
    limitedSink.scalar(null)
    limitedSink.end()

    assertThat(limitedSink.argumentsOrThrow()).isEqualTo(mapOf("" to listOf(true, null)))
  }

  @Test
  fun `scalar() - throws IllegalArgumentException for an unsupported type`() {
    sink.key("opaque")

    assertFailsWith<IllegalArgumentException> { sink.scalar(Any()) }
  }

  @Test
  fun `scalar() - throws IllegalStateException for a map value written without a key`() {
    assertFailsWith<IllegalStateException> { sink.scalar(1) }
  }

  // Computed scalars

  @Test
  fun `computedScalar() - writes the computed value as scalar() would`() {
    sink.key("text")
    sink.computedScalar { StringBuilder("hello") }
    sink.key("page")
    sink.computedScalar { 2 }

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("text" to "hello", "page" to 2))
    assertThat(arguments["text"]).isInstanceOf(String::class.java)
  }

  @Test
  fun `computedScalar() - spends characters for computed text`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(1))
    limitedSink.key("")

    val drop = assertFailsWith<DropArguments> { limitedSink.computedScalar { "ab" } }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
  }

  @Test
  fun `computedScalar() - drops the arguments without computing the value when the value budget is spent`() {
    val limitedSink = TreeArgumentWriter(budgetWithValuesRemaining(0))
    limitedSink.key("a")
    var computations = 0

    val drop = assertFailsWith<DropArguments> { limitedSink.computedScalar { computations++ } }

    assertThat(drop.signal).isEqualTo(maxCountDrop)
    assertThat(computations).isEqualTo(0)
  }

  @Test
  fun `computedScalar() - drops the arguments for invalid structure without computing a value nested too deeply`() {
    sink.key("nested")
    openLists(MAX_ARGUMENT_DEPTH)
    var computations = 0

    val drop = assertFailsWith<DropArguments> { sink.computedScalar { computations++ } }

    assertThat(drop.signal).isEqualTo(structureDrop)
    assertThat(computations).isEqualTo(0)
  }

  @Test
  fun `computedScalar() - drops the arguments for a duplicate key without computing the value`() {
    sink.key("id")
    sink.scalar(1)
    sink.key("id")
    var computations = 0

    val drop = assertFailsWith<DropArguments> { sink.computedScalar { computations++ } }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
    assertThat(computations).isEqualTo(0)
  }

  @Test
  fun `computedScalar() - drops the arguments for a duplicate key when computing the value would throw`() {
    sink.key("id")
    sink.scalar(1)
    sink.key("id")

    val drop =
      assertFailsWith<DropArguments> {
        sink.computedScalar { throw IllegalStateException("host toString() failed") }
      }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
  }

  @Test
  fun `computedScalar() - rethrows an earlier drop without computing the value`() {
    val drop = exhaustValues()
    var computations = 0

    val thrown = assertFailsWith<DropArguments> { sink.computedScalar { computations++ } }

    assertThat(thrown).isSameInstanceAs(drop)
    assertThat(computations).isEqualTo(0)
  }

  // Keys

  @Test
  fun `key() - accepts a key exactly as long as the remaining characters`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(2))

    limitedSink.key("ab")
    limitedSink.scalar(null)

    assertThat(limitedSink.argumentsOrThrow()).isEqualTo(mapOf("ab" to null))
  }

  @Test
  fun `key() - spends the characters of the key`() {
    val limitedSink = TreeArgumentWriter(budgetWithCharactersRemaining(1))

    val drop = assertFailsWith<DropArguments> { limitedSink.key("ab") }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
  }

  @Test
  fun `key() - doesn't spend a value`() {
    val limitedSink = TreeArgumentWriter(budgetWithValuesRemaining(0))

    limitedSink.key("a")

    val drop = assertFailsWith<DropArguments> { limitedSink.scalar(1) }
    assertThat(drop.signal).isEqualTo(maxCountDrop)
  }

  // Containers

  @Test
  fun `beginMap() - spends one value`() {
    val limitedSink = TreeArgumentWriter(budgetWithValuesRemaining(0))
    limitedSink.key("map")

    val drop = assertFailsWith<DropArguments> { limitedSink.beginMap() }

    assertThat(drop.signal).isEqualTo(maxCountDrop)
  }

  @Test
  fun `beginList() - spends one value`() {
    val limitedSink = TreeArgumentWriter(budgetWithValuesRemaining(0))
    limitedSink.key("list")

    val drop = assertFailsWith<DropArguments> { limitedSink.beginList() }

    assertThat(drop.signal).isEqualTo(maxCountDrop)
  }

  @Test
  fun `the root map doesn't spend a value`() {
    val limitedSink = TreeArgumentWriter(budgetWithValuesRemaining(1))
    limitedSink.key("id")

    limitedSink.scalar(1)

    assertThat(limitedSink.argumentsOrThrow()).isEqualTo(mapOf("id" to 1))
  }

  @Test
  fun `end() - throws IllegalStateException when no map or list is open`() {
    assertFailsWith<IllegalStateException> { sink.end() }
  }

  @Test
  fun `end() - throws IllegalStateException after every opened map and list was ended`() {
    sink.key("list")
    sink.beginList()
    sink.end()

    assertFailsWith<IllegalStateException> { sink.end() }
  }

  // Depth

  @Test
  fun `a scalar at the maximum depth is written`() {
    sink.key("nested")
    openLists(MAX_ARGUMENT_DEPTH - 1)

    sink.scalar("leaf")

    assertThat(sink.argumentsOrThrow())
      .isEqualTo(mapOf("nested" to nestedInLists(MAX_ARGUMENT_DEPTH - 1, "leaf")))
  }

  @Test
  fun `a scalar past the maximum depth drops the arguments for invalid structure`() {
    sink.key("nested")
    openLists(MAX_ARGUMENT_DEPTH)

    val drop = assertFailsWith<DropArguments> { sink.scalar("leaf") }

    assertThat(drop.signal).isEqualTo(structureDrop)
  }

  @Test
  fun `a container at the maximum depth is written`() {
    sink.key("nested")
    openLists(MAX_ARGUMENT_DEPTH - 1)

    sink.beginMap()
    sink.end()

    assertThat(sink.argumentsOrThrow())
      .isEqualTo(mapOf("nested" to nestedInLists(MAX_ARGUMENT_DEPTH - 1, emptyMap<String, Any?>())))
  }

  @Test
  fun `a container past the maximum depth drops the arguments for invalid structure`() {
    sink.key("nested")
    openLists(MAX_ARGUMENT_DEPTH)

    val drop = assertFailsWith<DropArguments> { sink.beginMap() }

    assertThat(drop.signal).isEqualTo(structureDrop)
  }

  @Test
  fun `depth counts only containers that are still open`() {
    sink.key("closed")
    openLists(MAX_ARGUMENT_DEPTH - 1)
    repeat(MAX_ARGUMENT_DEPTH - 1) { sink.end() }
    sink.key("nested")
    openLists(MAX_ARGUMENT_DEPTH - 1)

    sink.scalar("leaf")

    assertThat(sink.argumentsOrThrow()["nested"])
      .isEqualTo(nestedInLists(MAX_ARGUMENT_DEPTH - 1, "leaf"))
  }

  // Cycles

  @Test
  fun `a container nested in a container with the same identity drops the arguments for invalid structure`() {
    val list = mutableListOf<Any?>()
    sink.key("cyclic")
    sink.beginList(identity = list)

    val drop = assertFailsWith<DropArguments> { sink.beginList(identity = list) }

    assertThat(drop.signal).isEqualTo(structureDrop)
  }

  @Test
  fun `a container with the root identity drops the arguments for invalid structure`() {
    val root = mutableMapOf<String, Any?>()
    val rootedSink = TreeArgumentWriter(budget, rootIdentity = root)
    rootedSink.key("self")

    val drop = assertFailsWith<DropArguments> { rootedSink.beginMap(identity = root) }

    assertThat(drop.signal).isEqualTo(structureDrop)
  }

  @Test
  fun `a container identical to the root is written when no root identity is given`() {
    val root = mutableMapOf<String, Any?>()
    sink.key("self")

    sink.beginMap(identity = root)
    sink.end()

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf("self" to emptyMap<String, Any?>()))
  }

  @Test
  fun `sibling containers with the same identity are both written`() {
    val shared = listOf("a")
    sink.key("first")
    sink.beginList(identity = shared)
    sink.scalar("a")
    sink.end()
    sink.key("second")

    sink.beginList(identity = shared)
    sink.scalar("a")
    sink.end()

    assertThat(sink.argumentsOrThrow())
      .isEqualTo(mapOf("first" to listOf("a"), "second" to listOf("a")))
  }

  @Test
  fun `nested containers without an identity are never treated as a cycle`() {
    sink.key("nested")
    sink.beginList(identity = null)

    sink.beginList(identity = null)
    sink.end()
    sink.end()

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf("nested" to listOf(emptyList<Any?>())))
  }

  // Duplicate keys

  @Test
  fun `a scalar under a key already in its map drops the arguments for a duplicate key`() {
    sink.key("id")
    sink.scalar(1)
    sink.key("id")

    val drop = assertFailsWith<DropArguments> { sink.scalar(2) }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
  }

  @Test
  fun `a map under a key already in its map drops the arguments for a duplicate key when the map begins`() {
    sink.key("filters")
    sink.scalar(null)
    sink.key("filters")

    val drop = assertFailsWith<DropArguments> { sink.beginMap() }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
  }

  @Test
  fun `a list under a key already in its map drops the arguments for a duplicate key when the list begins`() {
    sink.key("tags")
    sink.beginList()
    sink.end()
    sink.key("tags")

    val drop = assertFailsWith<DropArguments> { sink.beginList() }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
  }

  @Test
  fun `the same key in a nested map and its parent map is written to both`() {
    sink.key("id")
    sink.beginMap()
    sink.key("id")
    sink.scalar(1)
    sink.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments).isEqualTo(mapOf("id" to mapOf("id" to 1)))
  }

  @Test
  fun `the same key in sibling maps is written to both`() {
    sink.key("first")
    sink.beginMap()
    sink.key("id")
    sink.scalar(1)
    sink.end()
    sink.key("second")
    sink.beginMap()
    sink.key("id")
    sink.scalar(2)
    sink.end()

    val arguments = sink.argumentsOrThrow()

    assertThat(arguments)
      .isEqualTo(mapOf("first" to mapOf("id" to 1), "second" to mapOf("id" to 2)))
  }

  @Test
  fun `a substitute value under a substitute key already holding the substitute is written once`() {
    sink.key(SUBSTITUTE)
    sink.scalar(SUBSTITUTE)
    sink.key(SUBSTITUTE)

    sink.scalar(SUBSTITUTE)

    assertThat(sink.argumentsOrThrow()).isEqualTo(mapOf(SUBSTITUTE to SUBSTITUTE))
  }

  @Test
  fun `a substitute value under a substitute key holding another value drops the arguments for a duplicate key`() {
    sink.key(SUBSTITUTE)
    sink.scalar("secret")
    sink.key(SUBSTITUTE)

    val drop = assertFailsWith<DropArguments> { sink.scalar(SUBSTITUTE) }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
  }

  @Test
  fun `another value under a substitute key holding the substitute drops the arguments for a duplicate key`() {
    sink.key(SUBSTITUTE)
    sink.scalar(SUBSTITUTE)
    sink.key(SUBSTITUTE)

    val drop = assertFailsWith<DropArguments> { sink.scalar("secret") }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
  }

  @Test
  fun `a substitute value under another key holding the substitute drops the arguments for a duplicate key`() {
    sink.key("token")
    sink.scalar(SUBSTITUTE)
    sink.key("token")

    val drop = assertFailsWith<DropArguments> { sink.scalar(SUBSTITUTE) }

    assertThat(drop.signal).isEqualTo(duplicateKeyDrop)
  }

  // Value budget

  @Test
  fun `values exactly filling the remaining value budget are written`() {
    val limitedSink = TreeArgumentWriter(budgetWithValuesRemaining(2))
    limitedSink.key("list")
    limitedSink.beginList()

    limitedSink.scalar(1)
    limitedSink.end()

    assertThat(limitedSink.argumentsOrThrow()).isEqualTo(mapOf("list" to listOf(1)))
  }

  // Shared budget

  @Test
  fun `sinks built on one budget share the value budget`() {
    val first = TreeArgumentWriter(budget)
    val second = TreeArgumentWriter(budget)
    writeScalars(first, count = MAX_ARGUMENT_VALUES - 1)
    writeScalars(second, count = 1)
    second.key("overflow")

    val drop = assertFailsWith<DropArguments> { second.scalar(1) }

    assertThat(drop.signal).isEqualTo(maxCountDrop)
  }

  @Test
  fun `sinks built on one budget share the character budget`() {
    val first = TreeArgumentWriter(budget)
    val second = TreeArgumentWriter(budget)
    first.key("x".repeat(MAX_ARGUMENT_CHARACTERS - 1))
    first.scalar(null)
    second.key("y")

    val drop = assertFailsWith<DropArguments> { second.scalar("z") }

    assertThat(drop.signal).isEqualTo(characterLimitDrop)
  }

  // Sticky failure

  @Test
  fun `every write after the arguments are dropped rethrows the same DropArguments`() {
    val drop = exhaustValues()

    assertThat(assertFailsWith<DropArguments> { sink.key("a") }).isSameInstanceAs(drop)
    assertThat(assertFailsWith<DropArguments> { sink.scalar(1) }).isSameInstanceAs(drop)
    assertThat(assertFailsWith<DropArguments> { sink.beginMap() }).isSameInstanceAs(drop)
    assertThat(assertFailsWith<DropArguments> { sink.beginList() }).isSameInstanceAs(drop)
    assertThat(assertFailsWith<DropArguments> { sink.end() }).isSameInstanceAs(drop)
  }

  @Test
  fun `argumentsOrThrow() - rethrows the DropArguments a source caught and swallowed`() {
    val caught = exhaustValues()

    val drop = assertFailsWith<DropArguments> { sink.argumentsOrThrow() }

    assertThat(drop).isSameInstanceAs(caught)
  }

  // Recorded failure

  @Test
  fun `failure - is null while every write is within the limits`() {
    sink.key("id")
    sink.scalar("123")

    assertThat(sink.failure).isNull()
  }

  @Test
  fun `failure - is the DropArguments thrown by the write that broke a limit`() {
    val drop = exhaustValues()

    assertThat(sink.failure).isSameInstanceAs(drop)
  }

  @Test
  fun `failure - stays the first DropArguments after later writes throw`() {
    val drop = exhaustValues()
    assertFailsWith<DropArguments> { sink.key("after") }
    assertFailsWith<DropArguments> { sink.scalar("x".repeat(MAX_ARGUMENT_CHARACTERS)) }

    val failure = sink.failure

    assertThat(failure).isSameInstanceAs(drop)
  }

  @Test
  fun `failure - stays null after a write throws for an unsupported value`() {
    sink.key("opaque")
    assertFailsWith<IllegalArgumentException> { sink.scalar(Any()) }

    val failure = sink.failure

    assertThat(failure).isNull()
  }

  // Limits a source enforces itself

  @Test
  fun `reject() - throws DropArguments with the given reason and warning`() {
    val drop = rejectStructure()

    assertThat(drop.signal).isEqualTo(structureDrop)
  }

  @Test
  fun `reject() - records the DropArguments it throws as the failure`() {
    val drop = rejectStructure()

    assertThat(sink.failure).isSameInstanceAs(drop)
  }

  @Test
  fun `reject() - throws and keeps the earlier DropArguments when a write already broke a limit`() {
    val writeDrop = exhaustValues()

    val drop = rejectStructure()

    assertThat(drop).isSameInstanceAs(writeDrop)
    assertThat(sink.failure).isSameInstanceAs(writeDrop)
  }

  @Test
  fun `reject() - throws and keeps the earlier DropArguments when a limit was already rejected`() {
    val firstDrop = rejectStructure()

    val drop =
      assertFailsWith<DropArguments> {
        sink.reject(ArgumentDropReason.MAX_COUNT, DropArguments.MAX_COUNT_WARNING)
      }

    assertThat(drop).isSameInstanceAs(firstDrop)
    assertThat(sink.failure).isSameInstanceAs(firstDrop)
  }

  @Test
  fun `every write after a rejected limit rethrows the rejected DropArguments`() {
    val drop = rejectStructure()

    assertThat(assertFailsWith<DropArguments> { sink.key("a") }).isSameInstanceAs(drop)
    assertThat(assertFailsWith<DropArguments> { sink.scalar(1) }).isSameInstanceAs(drop)
    assertThat(assertFailsWith<DropArguments> { sink.beginMap() }).isSameInstanceAs(drop)
    assertThat(sink.failure).isSameInstanceAs(drop)
  }

  @Test
  fun `argumentsOrThrow() - rethrows the rejected DropArguments a source caught and swallowed`() {
    sink.key("id")
    sink.scalar("123")
    val rejected = rejectStructure()

    val drop = assertFailsWith<DropArguments> { sink.argumentsOrThrow() }

    assertThat(drop).isSameInstanceAs(rejected)
  }

  // DropArguments

  @Test
  fun `DropArguments - has no stack trace`() {
    val drop = exhaustValues()

    assertThat(drop.stackTrace).isEmpty()
  }

  /** Opens [count] nested lists, so the next value has a depth of `count + 1`. */
  private fun openLists(count: Int) {
    repeat(count) { sink.beginList() }
  }

  private fun writeScalars(target: ArgumentSink, count: Int) {
    repeat(count) {
      target.key("k$it")
      target.scalar(it)
    }
  }

  /** Writes one value more than the value budget allows and returns the resulting drop. */
  private fun exhaustValues(): DropArguments =
    assertFailsWith<DropArguments> { writeScalars(sink, count = MAX_ARGUMENT_VALUES + 1) }

  /** Rejects the entry for invalid structure, as a source would, and returns the resulting drop. */
  private fun rejectStructure(): DropArguments =
    assertFailsWith<DropArguments> {
      sink.reject(ArgumentDropReason.INVALID_STRUCTURE, DropArguments.STRUCTURE_WARNING)
    }

  private fun nestedInLists(levels: Int, leaf: Any?): Any? {
    var nested = leaf
    repeat(levels) { nested = listOf(nested) }
    return nested
  }
}
