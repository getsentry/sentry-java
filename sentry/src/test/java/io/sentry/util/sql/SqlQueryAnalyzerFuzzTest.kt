package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import io.sentry.util.sql.SqlDialect.Support
import java.util.Random
import org.junit.Test

/**
 * Owns stability and leaks for generated input: [SqlQueryAnalyzer.analyze] must not throw, must not
 * emit a canary placed inside a literal or comment, must respect `maxQueryTextLength`, and must
 * finish each input within [TIME_BOUND_MILLIS] (plan 01, step 6).
 *
 * Each test runs a fixed seed and a run seed. The run seed is random unless the
 * `sentry.sql.fuzz.seed` system property sets it; every failure message prints the seed and the
 * command that reproduces it.
 */
class SqlQueryAnalyzerFuzzTest {

  @Test
  fun `token inputs never throw, leak a canary, exceed the limit or the time bound`() {
    AnalyzerWorker().use { worker ->
      warmUp(worker)
      for (seed in seeds()) {
        val random = Random(seed)
        repeat(TOKEN_INPUTS) { index ->
          val (name, dialect) = pickDialect(random)
          val limit = pickLimit(random)
          val input = TokenInputGenerator(random, dialect).generate()
          assertRobust(worker, seed, index, name, dialect, limit, input, canary = true)
        }
      }
    }
  }

  @Test
  fun `random UTF-16 inputs never throw, exceed the limit or the time bound`() {
    AnalyzerWorker().use { worker ->
      warmUp(worker)
      for (seed in seeds()) {
        val random = Random(seed)
        repeat(UTF16_INPUTS) { index ->
          val (name, dialect) = pickDialect(random)
          val limit = pickLimit(random)
          val input = String(CharArray(UTF16_LENGTH) { random.nextInt(0x10000).toChar() })
          assertRobust(worker, seed, index, name, dialect, limit, input, canary = false)
        }
      }
    }
  }

  private fun assertRobust(
    worker: AnalyzerWorker,
    seed: Long,
    index: Int,
    dialectName: String,
    dialect: SqlDialect,
    limit: Int,
    input: String,
    canary: Boolean,
  ) {
    val describe = {
      "seed=$seed index=$index dialect=$dialectName maxQueryTextLength=$limit " +
        "input=\"${escape(input)}\"\nReproduce: ./gradlew :sentry:test " +
        "--tests '${SqlQueryAnalyzerFuzzTest::class.java.name}' -D$SEED_PROPERTY=$seed"
    }
    val query = worker.analyze(input, dialect, limit, TIME_BOUND_MILLIS, describe)

    assertWithMessage("text longer than the limit; %s", describe())
      .that(query.queryText?.length ?: 0)
      .isAtMost(limit)
    if (!canary) return
    for ((field, value) in query.fields()) {
      val leaked = CANARY_FRAGMENTS.firstOrNull { value?.contains(it) == true } ?: continue
      assertWithMessage("canary fragment '%s' in %s; %s", leaked, field, describe())
        .that(value)
        .doesNotContain(leaked)
    }
  }

  /** Untimed runs so class loading and JIT compilation don't count against the first input. */
  private fun warmUp(worker: AnalyzerWorker) {
    val random = Random(WARM_UP_SEED)
    repeat(WARM_UP_INPUTS) {
      val (name, dialect) = pickDialect(random)
      val input = TokenInputGenerator(random, dialect).generate()
      worker.analyze(input, dialect, Int.MAX_VALUE, WARM_UP_TIMEOUT_MILLIS) {
        "warm-up dialect=$name input=\"${escape(input)}\""
      }
    }
  }

  private fun seeds(): List<Long> {
    val runSeed =
      System.getProperty(SEED_PROPERTY)?.takeIf { it.isNotBlank() }?.toLong() ?: Random().nextLong()
    return listOf(FIXED_SEED, runSeed)
  }

  private fun pickDialect(random: Random): Map.Entry<String, SqlDialect> =
    ALL_DIALECTS.entries.elementAt(random.nextInt(ALL_DIALECTS.size))

  /** Half the inputs are unlimited; the rest get small limits so truncation is exercised. */
  private fun pickLimit(random: Random): Int =
    if (random.nextBoolean()) Int.MAX_VALUE else 1 + random.nextInt(200)

  companion object {
    const val SEED_PROPERTY = "sentry.sql.fuzz.seed"

    /** Bound recorded in plan 01, step 6. */
    const val TIME_BOUND_MILLIS = 250L

    private const val FIXED_SEED = 6019L
    private const val WARM_UP_SEED = 712L
    private const val WARM_UP_INPUTS = 200
    private const val WARM_UP_TIMEOUT_MILLIS = 10_000L
    private const val TOKEN_INPUTS = 2_000
    private const val UTF16_INPUTS = 200
    private const val UTF16_LENGTH = 1_000

    const val CANARY = "Zq9xK7"
    private val CANARY_FRAGMENTS = fragments(CANARY)
  }
}

/**
 * Generates whitespace-separated token soup over keywords, identifiers, operators, parameter
 * markers, and every quote and comment construct.
 *
 * A canary goes only where the dialect makes a correct analyzer redact it: inside a literal or
 * comment, inside a construct whose rule is `UNKNOWN` (the analyzer stops there), or inside the
 * last construct when it is left unterminated (R4). Constructs before the last are always closed,
 * and their content cannot close them early, so a stray quote can't shift the lexer state for later
 * canaries; that would report a generator artifact, not a leak.
 */
private class TokenInputGenerator(private val random: Random, private val dialect: SqlDialect) {
  private val out = StringBuilder()

  fun generate(): String {
    if (random.nextInt(10) != 0) append(pick(LEADING_KEYWORDS))
    repeat(random.nextInt(MAX_SEGMENTS)) {
      if (random.nextInt(3) == 0) append(construct(terminated = true))
      else append(pick(FREE_TOKENS))
    }
    if (random.nextInt(3) == 0) append(construct(terminated = false))
    return out.toString()
  }

  private fun append(token: String) {
    if (out.isNotEmpty()) out.append(pick(SEPARATORS))
    out.append(token)
  }

  private fun construct(terminated: Boolean): String =
    when (random.nextInt(10)) {
      0 -> singleQuoted(terminated)
      1 -> doubleQuoted(terminated)
      2 -> identifier("`", "`", terminated)
      3 -> bracket(terminated)
      4 -> dollarQuoted(terminated)
      5 -> blockComment(terminated, depth = 0)
      6 -> dashComment(terminated)
      7 -> lineComment("#", dialect.hashComments, terminated)
      8 -> lineComment("//", dialect.doubleSlashComments, terminated)
      else -> alternativeQuoted(terminated)
    }

  private fun singleQuoted(terminated: Boolean): String {
    val prefix = pick(STRING_PREFIXES)
    // E'…' always treats `\` as an escape; leave it out so `\'` can't end the literal elsewhere.
    val pieces = if (prefix == "E") SINGLE_QUOTED.filter { it != "\\" } else SINGLE_QUOTED
    return prefix + "'" + content(pieces, canary = true) + close("'", terminated)
  }

  private fun doubleQuoted(terminated: Boolean): String =
    if (dialect.doubleQuotedIdentifiers == Support.YES) identifier("\"", "\"", terminated)
    else "\"" + content(DOUBLE_QUOTED, canary = true) + close("\"", terminated)

  /** Identifier content is kept, so a canary only goes into an unterminated one (R4). */
  private fun identifier(open: String, close: String, terminated: Boolean): String =
    open + content(IDENTIFIER_WORDS, canary = !terminated) + close(close, terminated)

  private fun bracket(terminated: Boolean): String {
    if (dialect.bracketIdentifiers == Support.YES) return identifier("[", "]", terminated)
    // `[` is punctuation (NO) or punctuation that stops before a quote (UNKNOWN): a literal
    // inside is redacted either way.
    val inner =
      if (random.nextBoolean()) singleQuoted(terminated = true) else pick(IDENTIFIER_WORDS)
    return "[ " + inner + close(" ]", terminated)
  }

  private fun dollarQuoted(terminated: Boolean): String {
    val delimiter = if (random.nextBoolean()) "$$" else "\$t$"
    if (dialect.dollarQuotedStrings == Support.NO) {
      // `$` is an identifier character here: the content is plain code.
      return delimiter + " " + content(IDENTIFIER_WORDS, canary = false) + " " + delimiter
    }
    return delimiter +
      " " +
      content(DOLLAR_QUOTED, canary = true) +
      close(" $delimiter", terminated)
  }

  private fun blockComment(terminated: Boolean, depth: Int): String {
    val content = StringBuilder(content(BLOCK_COMMENT, canary = true))
    // Under NO, the inner `*/` would end the outer comment and expose the rest.
    if (depth < 2 && dialect.nestedBlockComments != Support.NO && random.nextInt(4) == 0) {
      content.append(' ').append(blockComment(terminated = true, depth = depth + 1)).append(' ')
      content.append(content(BLOCK_COMMENT, canary = true))
    }
    return pick(BLOCK_OPENERS) + " " + content + close(" */", terminated)
  }

  private fun dashComment(terminated: Boolean): String {
    // MySQL reads `--x` as two minus signs; elsewhere it is a comment or a stop (UNKNOWN).
    val opener =
      if (dialect.dashDashCommentNeedsWhitespace != Support.YES && random.nextBoolean()) "--"
      else "-- "
    return opener + content(LINE_COMMENT, canary = true) + close("\n", terminated)
  }

  private fun lineComment(opener: String, rule: Support, terminated: Boolean): String =
    if (rule == Support.NO) {
      // Not a comment here: the content is plain code.
      opener + " " + content(IDENTIFIER_WORDS, canary = false) + "\n"
    } else {
      opener + content(LINE_COMMENT, canary = true) + close("\n", terminated)
    }

  /**
   * Oracle `q'<d>…<d>'`. Where the rule is NO, `q` is an identifier and `'<d>…<d>'` an ordinary
   * literal, so the content must not contain `'` there.
   */
  private fun alternativeQuoted(terminated: Boolean): String {
    val (open, closeDelimiter) = pick(ALTERNATIVE_DELIMITERS)
    var pieces = ALTERNATIVE_QUOTED.filter { closeDelimiter !in it }
    if (dialect.alternativeQuoting == Support.NO) pieces = pieces.filter { '\'' !in it }
    return pick(ALTERNATIVE_PREFIXES) +
      "'" +
      open +
      content(pieces, canary = true) +
      close("$closeDelimiter'", terminated)
  }

  private fun close(closer: String, terminated: Boolean): String = if (terminated) closer else ""

  private fun content(pieces: List<String>, canary: Boolean): String {
    val parts = MutableList(random.nextInt(6)) { pick(pieces) }
    if (canary && random.nextInt(3) != 0) {
      parts.add(random.nextInt(parts.size + 1), SqlQueryAnalyzerFuzzTest.CANARY)
    }
    return parts.joinToString("")
  }

  private fun <T> pick(values: List<T>): T = values[random.nextInt(values.size)]

  companion object {
    private const val MAX_SEGMENTS = 60

    private val SEPARATORS = listOf(" ", " ", " ", "\n", "\t", "  ", "\r\n")

    private val LEADING_KEYWORDS =
      listOf("SELECT", "INSERT INTO", "UPDATE", "DELETE FROM", "CALL", "EXEC", "WITH", "CREATE")

    private val FREE_TOKENS =
      listOf(
        "SELECT",
        "FROM",
        "WHERE",
        "INSERT",
        "INTO",
        "VALUES",
        "UPDATE",
        "SET",
        "DELETE",
        "CALL",
        "EXEC",
        "WITH",
        "AS",
        "JOIN",
        "ON",
        "IN",
        "AND",
        "OR",
        "NOT",
        "NULL",
        "TRUE",
        "CREATE",
        "TABLE",
        "USER",
        "PASSWORD",
        "IDENTIFIED",
        "BY",
        "GRANT",
        "TO",
        "UNION",
        "users",
        "t",
        "orders",
        "col_a",
        "straße",
        "x1",
        "1",
        "-2.5",
        "1e-5",
        "0x1F",
        "0b101",
        "=",
        "<>",
        "+",
        "-",
        "*",
        "/",
        "%",
        ",",
        ".",
        ";",
        "(",
        ")",
        "{",
        "}",
        "?",
        "?1",
        ":name",
        "@p",
        "$1",
        "\\",
        "$",
        "*/",
        "]",
        "!",
        "~",
        "|",
        "||",
      )

    private val IDENTIFIER_WORDS = listOf("a", "users", "x1", "straße", "col name", "_t", " ")

    private val WORDS =
      listOf("a", "users", "secret", "x1", "straße", " ", "  ", "1", "2.5", ",", "(", ")", ";", "=")

    /** Inert inside a literal: other quotes, comment markers, dollar quotes, markers. */
    private val LITERAL_EXTRAS =
      listOf(
        "\"",
        "`",
        "/*",
        "*/",
        "--",
        "#",
        "//",
        "$$",
        "\$t$",
        "[",
        "]",
        "\n",
        "{",
        "}",
        "?",
        ":p",
        "@p",
        "$1",
      )

    private val SINGLE_QUOTED = WORDS + LITERAL_EXTRAS + listOf("''", "\\")
    private val DOUBLE_QUOTED =
      WORDS + LITERAL_EXTRAS.filter { '"' !in it } + listOf("'", "\"\"", "\\")
    private val DOLLAR_QUOTED = WORDS + LITERAL_EXTRAS.filter { '$' !in it } + listOf("'", "\\")
    private val BLOCK_COMMENT = WORDS + (LITERAL_EXTRAS + listOf("'", "\\")).filter { '*' !in it }
    private val LINE_COMMENT = WORDS + (LITERAL_EXTRAS + listOf("'", "\\")).filter { '\n' !in it }
    private val ALTERNATIVE_QUOTED = WORDS + LITERAL_EXTRAS + listOf("'", "\\")

    private val STRING_PREFIXES = listOf("", "", "", "N", "E", "_utf8")
    private val BLOCK_OPENERS = listOf("/*", "/*", "/*!", "/*+")
    private val ALTERNATIVE_PREFIXES = listOf("q", "Q", "nq", "Nq")
    private val ALTERNATIVE_DELIMITERS =
      listOf("[" to ']', "{" to '}', "(" to ')', "<" to '>', "!" to '!')
  }
}
