package io.sentry.compose.navigation3

/**
 * A key for distinguishing back stacks over time.
 *
 * Lets `*Effect`s restart when either the identity of a stack entry changes or the stack's entries
 * are reordered.
 */
internal class BackStackKey<T : Any>(private val backStack: List<T>) {

  override fun equals(other: Any?): Boolean {
    // Use of identity rather than structural equality frees us from entries' equals() and
    // hashCode() implementations, which are provided by the host app and may be incomplete,
    // expensive, or incorrect for our purposes.
    if (this === other) {
      return true
    }
    if (other !is BackStackKey<*>) {
      return false
    }
    if (backStack.size != other.backStack.size) {
      return false
    }

    return backStack.indices.all { index -> backStack[index] === other.backStack[index] }
  }

  override fun hashCode(): Int {
    var result = backStack.size
    for (entry in backStack) {
      result = 31 * result + System.identityHashCode(entry)
    }
    return result
  }
}
