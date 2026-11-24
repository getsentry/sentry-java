package io.sentry.compose.navigation3.internal.backstack

import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.util.UrlUtils
import java.util.IdentityHashMap

/**
 * The core contract for the subsystem that converts properties of back stack entries into Sentry
 * arguments.
 *
 * An [ArgumentSource] pushes information about back stack properties into this sink. Sink
 * implementations are expected to materialize the provided info into a tree of Sentry arguments,
 * where the tree has a bounded size and height.
 *
 * **The argument conversion subsystem**
 *
 * With Nav3, host apps manage their own nav back stack, which they implement as a list of
 * arbitrarily typed entries. In order to be displayed in Sentry, each of those entries has to be
 * mapped to a Sentry name (e.g., ProductDetailScreen) and zero or more arguments (e.g., productId).
 * There are two ways for host apps to provide us with those mappings:
 *
 * 1. via a custom `BackStackEntryMapper`; or
 *
 * 2. via one of our own built-in mappers (viz., `BackStackEntryMapper.SerialName` or
 *    `BackStackEntryMapper.SerialNameAndProperties`).
 *
 * `ArgumentSink` and `ArgumentSource` create the seam that lets us support variable processing on
 * the source side (the host app's logic vs built-in logic) but uniform processing on the sink side
 * (argument pruning, protecting against constraint violation, and tree materialization).
 *
 * Here are the moving parts:
 * ```text
 *                     ArgumentSource
 *        Custom mapper              Built-in mappers
 *   ┌────────────────────┐        ┌───────────────────────┐
 *   │ HostArgumentSource │        │ KotlinxArgumentSource │
 *   │  (plain host map)  │        │   (drives encoder)    │
 *   └────────┬───────────┘        └────────┬──────────────┘
 *            │                             │ via
 *            │        KotlinxPropertiesEncoder + SensitiveKeyFilter
 *            │                             │
 *            └──────────────┬──────────────┘
 *                           ▼
 *           ┌───────────────────────────────┐
 *           │         ArgumentSink          │
 *           └───────────────────────────────┘
 *                           ▲  sole impl
 *           ┌───────────────┴───────────────┐
 *           │      TreeArgumentWriter       │
 *           │                               │           ┌────────────────┐
 *           │   Enforce + materialize:      │ spends ──▶│ ArgumentBudget │
 *           │   • builds the argument tree  │           └────────────────┘
 *           │   • prevents constraint       │           ┌────────────────┐
 *           │      violation (e.g., cycles) │ throws ──▶│ DropArguments  │
 *           └───────────────────────────────┘           └────────────────┘
 *
 *   Note: BackStackConverter.ArgumentSanitizer owns the ArgumentBudget and
 *   selects the ArgumentSource based on what the host app provides.
 * ```
 *
 * Receives one back stack entry's arguments as a [source][ArgumentSource] produces them, enforcing
 * the argument limits as it goes.
 *
 * Throws [DropArguments] when a write violates a constraint or [reject] is called. Every write
 * thereafter will also throw.
 */
internal interface ArgumentSink {

  /**
   * The first [DropArguments] thrown either by a write constraint violation or by a call to
   * [reject]; `null` if neither has occurred.
   *
   * Preserves the original drop reason even if intervening code (e.g., a host app serializer)
   * catches it and throws its own exception instead.
   */
  val failure: DropArguments?

  /**
   * Starts a map as the next value. [identity] is the object being written, if any, so that one
   * that contains itself is rejected instead of written until a limit is reached.
   *
   * Call [end] when finished.
   */
  fun beginMap(identity: Any? = null)

  /**
   * Starts a list as the next value. [identity] is as for [beginMap].
   *
   * Call [end] when finished.
   */
  fun beginList(identity: Any? = null)

  /**
   * Names the next value in the current map. If the map already has a value named [name], writing
   * the next value throws [DropArguments], as recording only one of them would show Sentry a
   * partial map as if it were complete.
   *
   * [describeDuplicate], if provided, returns the warning to log in that case instead of
   * [DropArguments.DUPLICATE_KEY_WARNING]. It's only called for a duplicate, so it may do work that
   * would be wasteful for every key, e.g., finding the key that [name] collides with.
   */
  fun key(name: String, describeDuplicate: (() -> String)? = null)

  /**
   * Writes [value] as the next value. Supports `null`, [Boolean], [Number], [CharSequence], and
   * [Char]; text is recorded as a [String].
   */
  fun scalar(value: Any?)

  /**
   * Writes the value [compute] returns as the next value, as for [scalar]. A sink that enforces
   * limits checks that the value can be written before calling [compute], so that host app code
   * (e.g., a `toString()`) doesn't run for a value that won't be recorded, and can't replace the
   * limit's drop reason with its own failure.
   */
  fun computedScalar(compute: () -> Any?) {
    scalar(compute())
  }

  /** Ends the most recently started map or list. */
  fun end()

  /**
   * Reports the violation of a constraint that a source enforces itself, rather than one broken via
   * a write (e.g., how deeply serializers nest), and throws the resulting [DropArguments].
   *
   * If a constraint was already violated, throws that instead.
   */
  fun reject(reason: ArgumentDropReason, warning: String): Nothing
}

/**
 * Writes a back stack entry's arguments to an [ArgumentSink].
 *
 * Sources decide which data to expose and whether to filter sensitive values. The sink applies size
 * and structural limits without performing sensitive-data filtering.
 */
internal interface ArgumentSource {
  fun writeTo(sink: ArgumentSink)
}

/**
 * Materializes an argument tree for a single back stack entry and enforces argument constraints.
 * Argument inputs are provided by an [ArgumentSource].
 *
 * **Argument constraints**
 *
 * Rejects cycles, excessive nesting, duplicate keys, and writes that exceed the value or character
 * limits in [budget]. Writes in violation of those limits throw [DropArguments], as does [reject]
 * for a constraint the source enforces itself. Once thrown, subsequent writes and
 * [argumentsOrThrow] rethrow that failure so a source can't accidentally return partial arguments.
 *
 * [budget] is shared across all back stack entries processed in one update, limiting their combined
 * value and character counts.
 *
 * [rootIdentity] identifies the source object represented by this entry's root argument map,
 * allowing references back to that object to be detected as cycles.
 *
 * **Key duplication**
 *
 * Duplicate keys are allowed only when both the key and the old and new values are
 * [UrlUtils.SENSITIVE_DATA_SUBSTITUTE], since replacing one redacted value with another loses no
 * information.
 */
@Suppress("TooManyFunctions")
internal class TreeArgumentWriter(
  private val budget: ArgumentBudget,
  rootIdentity: Any? = null,
) : ArgumentSink {

  private val root = LinkedHashMap<String, Any?>()

  /** The open containers, innermost last, with the identity each was written from. */
  private val containers = ArrayList<Any>().apply { add(root) }
  private val identities = ArrayList<Any?>().apply { add(rootIdentity) }
  private val activeIdentities =
    IdentityHashMap<Any, Unit>().apply { rootIdentity?.let { put(it, Unit) } }

  private var pendingKey: String? = null
  private var describePendingDuplicate: (() -> String)? = null

  override var failure: DropArguments? = null
    private set

  override fun beginMap(identity: Any?) = guarded { begin(LinkedHashMap<String, Any?>(), identity) }

  override fun beginList(identity: Any?) = guarded { begin(ArrayList<Any?>(), identity) }

  override fun key(name: String, describeDuplicate: (() -> String)?) = guarded {
    pendingKey = budget.spendText(name)
    describePendingDuplicate = describeDuplicate
  }

  override fun scalar(value: Any?) = writeScalar { value }

  override fun computedScalar(compute: () -> Any?) = writeScalar(compute)

  override fun end() = guarded {
    check(containers.size > 1) { "end() without an open map or list." }
    containers.removeAt(containers.lastIndex)
    identities.removeAt(identities.lastIndex)?.let(activeIdentities::remove)
  }

  override fun reject(reason: ArgumentDropReason, warning: String): Nothing {
    failure?.let { throw it }
    throw DropArguments(reason, warning).also { failure = it }
  }

  /**
   * Returns the entry's arguments, or re-throws the [DropArguments] a write threw, even if the
   * source caught it.
   *
   * Should only be called after the [ArgumentSource] has completed all writes and closed every
   * nested map and list.
   */
  fun argumentsOrThrow(): Map<String, Any?> {
    failure?.let { throw it }
    return root
  }

  private fun begin(container: Any, identity: Any?) {
    visitValue()
    if (identity != null && activeIdentities.put(identity, Unit) != null) {
      throw DropArguments(ArgumentDropReason.INVALID_STRUCTURE, DropArguments.STRUCTURE_WARNING)
    }
    add(container)
    containers += container
    identities += identity
  }

  /**
   * Enforces the depth limit, spends one value for the value about to be added, and rejects a key
   * that's already taken, before the value is computed.
   */
  private fun visitValue() {
    // A value added while n containers are open has a depth of n.
    if (containers.size > MAX_ARGUMENT_DEPTH) {
      throw DropArguments(ArgumentDropReason.INVALID_STRUCTURE, DropArguments.STRUCTURE_WARNING)
    }
    budget.spendValue()
    // A substitute key may still be written again with a substitute value, which add() decides
    // once the value is known.
    val key = pendingKey
    val isKeyTaken = key != null && (containers.last() as? Map<*, *>)?.containsKey(key) == true
    if (isKeyTaken && !key.isSubstitute()) {
      throw duplicateKey()
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun add(value: Any?) {
    when (val parent = containers.last()) {
      is ArrayList<*> -> (parent as ArrayList<Any?>) += value
      else -> {
        val key = checkNotNull(pendingKey) { "A map value was written without a key." }
        val map = parent as LinkedHashMap<String, Any?>
        val replacesSubstituteWithSubstitute =
          key.isSubstitute() && map[key].isSubstitute() && value.isSubstitute()
        if (map.containsKey(key) && !replacesSubstituteWithSubstitute) {
          throw duplicateKey()
        }
        map[key] = value
        pendingKey = null
        describePendingDuplicate = null
      }
    }
  }

  private fun duplicateKey(): DropArguments =
    DropArguments(
      ArgumentDropReason.DUPLICATE_KEY,
      describePendingDuplicate?.invoke() ?: DropArguments.DUPLICATE_KEY_WARNING,
    )

  private inline fun writeScalar(compute: () -> Any?) = guarded {
    visitValue()
    add(
      when (val value = compute()) {
        null,
        is Boolean -> value
        // JSON has no NaN or infinity, and Sentry's JSON writer throws for them, which would drop
        // the whole event these arguments are attached to. We record them as strings instead.
        is Number -> if (value.isNonFinite()) budget.spendText(value.toString()) else value
        is CharSequence -> budget.spendText(value)
        is Char -> budget.spendText(value.toString())
        else ->
          throw IllegalArgumentException("Unsupported scalar of type ${value.javaClass.name}.")
      },
    )
  }

  private inline fun guarded(write: () -> Unit) {
    failure?.let { throw it }
    try {
      write()
    } catch (e: DropArguments) {
      failure = e
      throw e
    }
  }
}

/**
 * Writes a host app's argument map to an [ArgumentSink].
 *
 * Nested maps are traversed recursively, collections and arrays become lists, and enums use their
 * names. Map keys and unsupported values are stringified; unsupported types are reported to
 * [onUnsupportedValue].
 *
 * Traversal and string conversion may invoke host app code.
 */
internal class HostArgumentSource(
  private val arguments: Map<*, *>,
  private val onUnsupportedValue: (typeName: String?) -> Unit,
) : ArgumentSource {

  override fun writeTo(sink: ArgumentSink) {
    writeEntries(arguments, sink)
  }

  private fun writeEntries(map: Map<*, *>, sink: ArgumentSink) {
    for ((key, value) in map) {
      val name = key.toString()
      sink.key(name) { duplicateKeyWarning(map, key, name) }
      write(value, sink)
    }
  }

  /**
   * Names only the colliding keys' types, as the keys themselves may contain host data. Collisions
   * are rare, so we re-scan [map] instead of tracking the original keys of every map.
   */
  private fun duplicateKeyWarning(map: Map<*, *>, key: Any?, name: String): String {
    val otherKey = map.keys.firstOrNull { it !== key && it.toString() == name }
    return "A Nav3 map key (${key?.let { it::class.simpleName }}) stringifies to the same " +
      "string as another key (${otherKey?.let { it::class.simpleName }}). Dropping this " +
      "entry's arguments."
  }

  private fun write(value: Any?, sink: ArgumentSink) {
    val collection = value?.asCollectionOrNull()
    when {
      value is Map<*, *> -> {
        sink.beginMap(identity = value)
        writeEntries(value, sink)
        sink.end()
      }
      collection != null -> {
        sink.beginList(identity = value)
        // Iterated without reading the collection's size, which the host app controls.
        for (element in collection) {
          write(element, sink)
        }
        sink.end()
      }
      // Computed only once the sink has checked the value fits, as it may run host app code.
      else -> sink.computedScalar { scalarOf(value) }
    }
  }

  private fun scalarOf(value: Any?): Any? =
    when (value) {
      null,
      is Boolean,
      is Number,
      is CharSequence,
      is Char -> value
      is Enum<*> -> value.name
      else -> {
        onUnsupportedValue(value::class.simpleName)
        value.toString()
      }
    }
}

/** Returns this collection, or this array as a list, or `null` if it's neither. */
private fun Any.asCollectionOrNull(): Collection<*>? =
  when (this) {
    is Collection<*> -> this
    is Array<*> -> asList()
    is BooleanArray -> asList()
    is ByteArray -> asList()
    is ShortArray -> asList()
    is IntArray -> asList()
    is LongArray -> asList()
    is FloatArray -> asList()
    is DoubleArray -> asList()
    is CharArray -> asList()
    else -> null
  }

/**
 * Control-flow signal to drop an entry's arguments. Overrides [fillInStackTrace] to skip
 * stack-trace capture.
 *
 * [warning] is logged when the arguments are dropped. Use `null` if no warning is needed, for
 * example when the source already logged the failure.
 */
internal class DropArguments(
  val reason: ArgumentDropReason,
  val warning: String?,
) : RuntimeException() {

  override fun fillInStackTrace(): Throwable = this

  companion object {

    const val DUPLICATE_KEY_WARNING =
      "Nav3 map keys stringify to the same string. Dropping this entry's arguments."

    const val MAX_CHARACTER_WARNING =
      "Nav3 back stack update exceeded the argument character limit. Dropping arguments for " +
        "this and older entries."

    const val MAX_COUNT_WARNING =
      "Nav3 back stack update exceeded the argument value limit. Dropping arguments for this " +
        "and older entries."

    const val STRUCTURE_WARNING =
      "Nav3 argument sanitization failed (possibly cyclic or too deeply nested). Dropping this " +
        "entry's arguments."
  }
}

/**
 * The value and character budgets shared by the entries of one back stack update. Throws
 * [DropArguments] when a value or text doesn't fit in what's left.
 */
internal class ArgumentBudget {

  private var remainingValues = MAX_ARGUMENT_VALUES
  private var remainingCharacters = MAX_ARGUMENT_CHARACTERS

  /** Spends one value. */
  fun spendValue() {
    if (--remainingValues < 0) {
      throw DropArguments(ArgumentDropReason.MAX_COUNT, DropArguments.MAX_COUNT_WARNING)
    }
  }

  /** Returns [text] after spending its characters. */
  fun spendText(text: String): String {
    checkFits(text.length)
    remainingCharacters -= text.length
    return text
  }

  /**
   * Returns [text] as a [String] after spending its characters.
   *
   * Checks [text]'s length before converting it, so that host app text that doesn't fit isn't
   * copied, and again after, in case a custom [CharSequence]'s length doesn't match its text.
   */
  fun spendText(text: CharSequence): String {
    checkFits(text.length)
    return spendText(text.toString())
  }

  private fun checkFits(characters: Int) {
    if (characters > remainingCharacters) {
      throw DropArguments(ArgumentDropReason.CHARACTER_LIMIT, DropArguments.MAX_CHARACTER_WARNING)
    }
  }
}

private fun Any?.isSubstitute(): Boolean = this == UrlUtils.SENSITIVE_DATA_SUBSTITUTE

/** Whether this is a NaN or infinite `Double` or `Float`. */
private fun Number.isNonFinite(): Boolean =
  (this is Double && !isFinite()) || (this is Float && !isFinite())
