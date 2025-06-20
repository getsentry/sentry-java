package io.sentry.apollo3

import com.apollographql.apollo3.api.Adapter
import com.apollographql.apollo3.api.CompiledField
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.json.JsonWriter
import com.apollographql.apollo3.api.obj
import io.sentry.apollo3.adapter.LaunchDetailsQuery_ResponseAdapter
import io.sentry.apollo3.adapter.LaunchDetailsQuery_VariablesAdapter
import io.sentry.apollo3.selections.LaunchDetailsQuerySelections
import kotlin.String

public data class LaunchDetailsQuery(public val id: String) : Query<LaunchDetailsQuery.Data> {
  public override fun id(): String = OPERATION_ID

  public override fun document(): String = OPERATION_DOCUMENT

  public override fun name(): String = OPERATION_NAME

  public override fun serializeVariables(
    writer: JsonWriter,
    customScalarAdapters: CustomScalarAdapters,
  ) {
    LaunchDetailsQuery_VariablesAdapter.toJson(writer, customScalarAdapters, this)
  }

  public override fun adapter(): Adapter<Data> = LaunchDetailsQuery_ResponseAdapter.Data.obj()

  public override fun rootField(): CompiledField =
    CompiledField.Builder(name = "data", type = io.sentry.apollo3.type.Query.type)
      .selections(selections = LaunchDetailsQuerySelections.root)
      .build()

  public data class Data(public val launch: Launch?) : Query.Data

  public data class Launch(
    public val id: String,
    public val site: String?,
    public val mission: Mission?,
    public val rocket: Rocket?,
  )

  public data class Mission(public val name: String?, public val missionPatch: String?)

  public data class Rocket(public val name: String?, public val type: String?)

  public companion object {
    public const val OPERATION_ID: String =
      "1b3bda4a2dcb47a77aa30346e10339d4600e0cbe9fa686867e9226e463b7118d"

    /**
     * The minimized GraphQL document being sent to the server to save a few bytes. The un-minimized
     * version is:
     *
     * query LaunchDetails($id: ID!) { launch(id: $id) { id site mission { name missionPatch(size:
     * LARGE) } rocket { name type } } }
     */
    public const val OPERATION_DOCUMENT: String =
      "query LaunchDetails(${'$'}id: ID!) { launch(id: ${'$'}id) { id site mission { name missionPatch(size: LARGE) } rocket { name type } } }"

    public const val OPERATION_NAME: String = "LaunchDetails"
  }
}
