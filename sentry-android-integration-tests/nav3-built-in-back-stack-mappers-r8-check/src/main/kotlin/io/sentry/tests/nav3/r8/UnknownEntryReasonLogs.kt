package io.sentry.tests.nav3.r8

/**
 * The `unknown_entry_reason` values this check expects, as `UnknownEntryReason` serializes them.
 */
const val ENTRY_NOT_ANNOTATED = "entry_not_annotated"
const val GENERIC_ENTRY_TYPE = "generic_entry_type"
const val SERIALIZER_NOT_FOUND = "serializer_not_found"
const val SERIALIZER_ACCESS_THREW = "serializer_access_threw"

/**
 * Returns the reason from the mapper's warning for [key]'s type, or `null` if none was logged.
 *
 * The reason is internal to the SDK, so the app reads the warning emitted by the branch that
 * selects it. Matches fixed fragments of `KotlinxMapperDiagnostics` messages; changes to those
 * fragments cause the check to fail.
 */
fun loggedUnknownEntryReason(logger: RecordingLogger, key: Any): String? {
  // The mapper looks up an enum constant's declaring class, as a constant with a body is an
  // instance of an anonymous subclass.
  val type = (key as? Enum<*>)?.declaringJavaClass ?: key.javaClass
  val message =
    logger.messages.toList().firstOrNull { "can't name entries of type ${type.name} because" in it }
      ?: return null
  return REASONS_BY_MESSAGE_FRAGMENT.entries.firstOrNull { it.key in message }?.value
}

private val REASONS_BY_MESSAGE_FRAGMENT =
  mapOf(
    "because the type is not @Serializable" to ENTRY_NOT_ANNOTATED,
    "because the type has type parameters" to GENERIC_ENTRY_TYPE,
    "because no serializer was found" to SERIALIZER_NOT_FOUND,
    "because reading its annotations, serializer, or descriptor failed" to SERIALIZER_ACCESS_THREW,
  )
