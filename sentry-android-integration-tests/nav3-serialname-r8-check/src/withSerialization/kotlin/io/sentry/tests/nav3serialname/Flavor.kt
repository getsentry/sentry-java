package io.sentry.tests.nav3serialname

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val FLAVOR = "withSerialization"

@Serializable data object PublicObject

@Serializable @SerialName("Named") data class NamedClass(val id: String)

@Serializable data class DefaultNamedClass(val id: String)

@Serializable
sealed interface Sealed {
  @Serializable data object Child : Sealed

  @Serializable data class Leaf(val id: String) : Sealed
}

@Serializable
enum class SerializableEnum {
  A
}

@Serializable data class Generic<T>(val value: T)

@Serializable private data object PrivateTopLevelObject

@Serializable private data class PrivateTopLevelClass(val id: String)

@Serializable
enum class SortOrder {
  @SerialName("ascending") ASC
}

@Serializable @JvmInline value class Price(val cents: Int)

@Serializable data class Filter(val sessionId: String, val query: String)

@Serializable
sealed interface Origin {
  @Serializable @SerialName("search") data class Search(val term: String) : Origin

  @Serializable @SerialName("deeplink") data object DeepLink : Origin
}

/** Uses every encoding rule of [BackStackEntryMapper.SerialNamesAndArguments], plus filtering. */
@Serializable
data class ArgumentsKey(
  val productId: Int,
  val authToken: String,
  val price: Price,
  val sort: SortOrder,
  val tags: List<String>,
  val sizes: Set<Int>,
  val counts: Map<String, Int>,
  val filter: Filter,
  val filters: List<Filter>,
  val origin: Origin,
  val fallbackOrigin: Origin,
  val note: String?,
  @Transient val cache: String = "not encoded",
)

class Holder {
  @Serializable private data object PrivateNestedObject

  @Serializable private data class PrivateNestedClass(val id: String)

  companion object {
    fun keys(): List<Expectation> =
      listOf(
        Expectation(
          "private nested object",
          PrivateNestedObject,
          "io.sentry.tests.nav3serialname.Holder.PrivateNestedObject",
        ),
        Expectation(
          "private nested class",
          PrivateNestedClass("1"),
          "io.sentry.tests.nav3serialname.Holder.PrivateNestedClass",
          mapOf("id" to "1"),
        ),
      )
  }
}

private const val PKG = "io.sentry.tests.nav3serialname"

private val ID_ARGUMENTS = mapOf("id" to "1")

private val argumentsKey =
  ArgumentsKey(
    productId = 42,
    authToken = "secret",
    price = Price(999),
    sort = SortOrder.ASC,
    tags = listOf("a", "b"),
    sizes = setOf(1, 2),
    counts = mapOf("apiKey" to 1, "views" to 2),
    filter = Filter(sessionId = "secret", query = "shoes"),
    filters = listOf(Filter(sessionId = "secret", query = "hats")),
    origin = Origin.Search("shoes"),
    fallbackOrigin = Origin.DeepLink,
    note = null,
  )

private val argumentsKeyArguments =
  mapOf(
    "productId" to 42,
    "authToken" to "[Filtered]",
    "price" to 999,
    "sort" to "ascending",
    "tags" to listOf("a", "b"),
    "sizes" to listOf(1, 2),
    "counts" to mapOf("apiKey" to "[Filtered]", "views" to 2),
    "filter" to mapOf("sessionId" to "[Filtered]", "query" to "shoes"),
    "filters" to listOf(mapOf("sessionId" to "[Filtered]", "query" to "hats")),
    "origin" to mapOf("type" to "search", "value" to mapOf("term" to "shoes")),
    "fallbackOrigin" to mapOf("type" to "deeplink", "value" to emptyMap<String, Any?>()),
    "note" to null,
  )

fun flavorExpectations(): List<Expectation> =
  listOf(
    Expectation("public data object", PublicObject, "$PKG.PublicObject"),
    Expectation("data class with @SerialName", NamedClass("1"), "Named", ID_ARGUMENTS),
    Expectation("data class", DefaultNamedClass("1"), "$PKG.DefaultNamedClass", ID_ARGUMENTS),
    Expectation("sealed child object", Sealed.Child, "$PKG.Sealed.Child"),
    Expectation("sealed leaf class", Sealed.Leaf("1"), "$PKG.Sealed.Leaf", ID_ARGUMENTS),
    Expectation(
      "serializable enum",
      SerializableEnum.A,
      "$PKG.SerializableEnum",
      mapOf("value" to "A"),
    ),
    Expectation("generic class", Generic("x"), null),
    Expectation("private top-level object", PrivateTopLevelObject, "$PKG.PrivateTopLevelObject"),
    Expectation(
      "private top-level class",
      PrivateTopLevelClass("1"),
      "$PKG.PrivateTopLevelClass",
      ID_ARGUMENTS,
    ),
    Expectation("arguments key", argumentsKey, "$PKG.ArgumentsKey", argumentsKeyArguments),
  ) + Holder.keys()

fun flavorMinificationChecks(): List<MinificationCheck> =
  listOf(
    MinificationCheck(
      "PublicObject was renamed (runtime class ${PublicObject::class.java.name})",
      PublicObject::class.java.name != "$PKG.PublicObject",
    ),
    // Argument names come from serializer descriptors, so they survive R8 renaming fields.
    MinificationCheck(
      "ArgumentsKey fields were renamed " +
        "(fields ${ArgumentsKey::class.java.declaredFields.map { it.name }})",
      ArgumentsKey::class.java.declaredFields.none { it.name == "productId" },
    ),
  )
