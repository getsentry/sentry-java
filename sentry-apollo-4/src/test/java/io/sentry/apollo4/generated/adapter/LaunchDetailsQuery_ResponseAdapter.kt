//
// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL version '3.3.0'.
//
package io.sentry.apollo4.generated.adapter

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.NullableStringAdapter
import com.apollographql.apollo.api.StringAdapter
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.JsonWriter
import com.apollographql.apollo.api.nullable
import com.apollographql.apollo.api.obj
import io.sentry.apollo4.generated.LaunchDetailsQuery
import kotlin.String
import kotlin.collections.List

public object LaunchDetailsQuery_ResponseAdapter {
    public object Data : Adapter<LaunchDetailsQuery.Data> {
        public val RESPONSE_NAMES: List<String> = listOf("launch")

        public override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters):
            LaunchDetailsQuery.Data {
            var launch: LaunchDetailsQuery.Launch? = null

            while (true) {
                when (reader.selectName(RESPONSE_NAMES)) {
                    0 -> launch = Launch.obj().nullable().fromJson(reader, customScalarAdapters)
                    else -> break
                }
            }

            return LaunchDetailsQuery.Data(
                launch = launch
            )
        }

        public override fun toJson(
            writer: JsonWriter,
            customScalarAdapters: CustomScalarAdapters,
            `value`: LaunchDetailsQuery.Data
        ) {
            writer.name("launch")
            Launch.obj().nullable().toJson(writer, customScalarAdapters, value.launch)
        }
    }

    public object Launch : Adapter<LaunchDetailsQuery.Launch> {
        public val RESPONSE_NAMES: List<String> = listOf("id", "site", "mission", "rocket")

        public override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters):
            LaunchDetailsQuery.Launch {
            var id: String? = null
            var site: String? = null
            var mission: LaunchDetailsQuery.Mission? = null
            var rocket: LaunchDetailsQuery.Rocket? = null

            while (true) {
                when (reader.selectName(RESPONSE_NAMES)) {
                    0 -> id = StringAdapter.fromJson(reader, customScalarAdapters)
                    1 -> site = NullableStringAdapter.fromJson(reader, customScalarAdapters)
                    2 -> mission = Mission.obj().nullable().fromJson(reader, customScalarAdapters)
                    3 -> rocket = Rocket.obj().nullable().fromJson(reader, customScalarAdapters)
                    else -> break
                }
            }

            return LaunchDetailsQuery.Launch(
                id = id!!,
                site = site,
                mission = mission,
                rocket = rocket
            )
        }

        public override fun toJson(
            writer: JsonWriter,
            customScalarAdapters: CustomScalarAdapters,
            `value`: LaunchDetailsQuery.Launch
        ) {
            writer.name("id")
            StringAdapter.toJson(writer, customScalarAdapters, value.id)

            writer.name("site")
            NullableStringAdapter.toJson(writer, customScalarAdapters, value.site)

            writer.name("mission")
            Mission.obj().nullable().toJson(writer, customScalarAdapters, value.mission)

            writer.name("rocket")
            Rocket.obj().nullable().toJson(writer, customScalarAdapters, value.rocket)
        }
    }

    public object Mission : Adapter<LaunchDetailsQuery.Mission> {
        public val RESPONSE_NAMES: List<String> = listOf("name", "missionPatch")

        public override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters):
            LaunchDetailsQuery.Mission {
            var name: String? = null
            var missionPatch: String? = null

            while (true) {
                when (reader.selectName(RESPONSE_NAMES)) {
                    0 -> name = NullableStringAdapter.fromJson(reader, customScalarAdapters)
                    1 -> missionPatch = NullableStringAdapter.fromJson(reader, customScalarAdapters)
                    else -> break
                }
            }

            return LaunchDetailsQuery.Mission(
                name = name,
                missionPatch = missionPatch
            )
        }

        public override fun toJson(
            writer: JsonWriter,
            customScalarAdapters: CustomScalarAdapters,
            `value`: LaunchDetailsQuery.Mission
        ) {
            writer.name("name")
            NullableStringAdapter.toJson(writer, customScalarAdapters, value.name)

            writer.name("missionPatch")
            NullableStringAdapter.toJson(writer, customScalarAdapters, value.missionPatch)
        }
    }

    public object Rocket : Adapter<LaunchDetailsQuery.Rocket> {
        public val RESPONSE_NAMES: List<String> = listOf("name", "type")

        public override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters):
            LaunchDetailsQuery.Rocket {
            var name: String? = null
            var type: String? = null

            while (true) {
                when (reader.selectName(RESPONSE_NAMES)) {
                    0 -> name = NullableStringAdapter.fromJson(reader, customScalarAdapters)
                    1 -> type = NullableStringAdapter.fromJson(reader, customScalarAdapters)
                    else -> break
                }
            }

            return LaunchDetailsQuery.Rocket(
                name = name,
                type = type
            )
        }

        public override fun toJson(
            writer: JsonWriter,
            customScalarAdapters: CustomScalarAdapters,
            `value`: LaunchDetailsQuery.Rocket
        ) {
            writer.name("name")
            NullableStringAdapter.toJson(writer, customScalarAdapters, value.name)

            writer.name("type")
            NullableStringAdapter.toJson(writer, customScalarAdapters, value.type)
        }
    }
}
