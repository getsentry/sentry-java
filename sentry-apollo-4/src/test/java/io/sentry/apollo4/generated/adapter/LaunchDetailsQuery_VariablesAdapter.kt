//
// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL version '3.3.0'.
//
package io.sentry.apollo4.generated.adapter

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.StringAdapter
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.JsonWriter
import io.sentry.apollo4.generated.LaunchDetailsQuery

object LaunchDetailsQuery_VariablesAdapter : Adapter<LaunchDetailsQuery> {
    override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters):
        LaunchDetailsQuery = throw IllegalStateException("Input type used in output position")

    override fun toJson(
        writer: JsonWriter,
        customScalarAdapters: CustomScalarAdapters,
        `value`: LaunchDetailsQuery
    ) {
        writer.name("id")
        StringAdapter.toJson(writer, customScalarAdapters, value.id)
    }
}
