package io.sentry.apollo4

/**
 * Common constants used across the module
 */
internal const val OPERATION_ID_HEADER_NAME = "SENTRY-APOLLO-4-OPERATION-ID"
internal const val OPERATION_NAME_HEADER_NAME = "SENTRY-APOLLO-4-OPERATION-NAME"
internal const val OPERATION_TYPE_HEADER_NAME = "SENTRY-APOLLO-4-OPERATION-TYPE"
internal const val VARIABLES_HEADER_NAME = "SENTRY-APOLLO-4-VARIABLES"
internal val INTERNAL_HEADER_NAMES by lazy {
    listOf(
        OPERATION_ID_HEADER_NAME,
        OPERATION_NAME_HEADER_NAME,
        OPERATION_TYPE_HEADER_NAME,
        VARIABLES_HEADER_NAME
    )
}
