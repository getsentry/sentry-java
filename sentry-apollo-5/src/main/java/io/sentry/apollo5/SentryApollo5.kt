package io.sentry.apollo5

import com.apollographql.apollo.api.ExecutionContext

internal data class SentryApollo5OperationContext(
  val operationId: String,
  val operationName: String,
  val operationType: String,
  val variables: String?,
) : ExecutionContext.Element {
  override val key: ExecutionContext.Key<*>
    get() = Key

  companion object Key : ExecutionContext.Key<SentryApollo5OperationContext>
}
