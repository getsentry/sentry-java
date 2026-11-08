package io.sentry.tests.nav3.r8

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
  A,
}

@Serializable data class Generic<T>(val value: T)

@Serializable private data object PrivateTopLevelObject

@Serializable private data class PrivateTopLevelClass(val id: String)

@Serializable
enum class SortOrder {
  @SerialName("ascending") ASC,
}

@Serializable @JvmInline value class Price(val cents: Int)

@Serializable data class Filter(val sessionId: String, val query: String)

@Serializable
sealed interface Origin {
  @Serializable @SerialName("search") data class Search(val term: String) : Origin

  @Serializable @SerialName("deeplink") data object DeepLink : Origin
}

/**
 * Has a named companion object but no named-companion R8 rules of the app's own.
 * kotlinx.serialization 1.10.0+'s own rules keep what its reflective serializer lookup needs, so
 * the mappers must name it. Before 1.10.0, minification may strip that (e.g., always with 1.7.3,
 * and with 1.9.x in R8 full mode), so falling back to `/unknown` is also accepted.
 */
@Serializable
data class NamedCompanionKey(val id: String) {
  companion object Factory {
    fun of(id: String): NamedCompanionKey = NamedCompanionKey(id)
  }
}

/**
 * Like [NamedCompanionKey], but covered by kotlinx.serialization's named-companion R8 rules
 * (`proguard-rules.pro`), as the
 * [troubleshooting docs](https://docs.sentry.io/platforms/android/integrations/navigation/#troubleshooting-kotlinxserialization-mappers)
 * tell apps to add.
 */
@Serializable
data class NamedCompanionKeyWithRules(val id: String) {
  companion object Factory {
    fun of(id: String): NamedCompanionKeyWithRules = NamedCompanionKeyWithRules(id)
  }
}

/** Uses every encoding rule of [BackStackEntryMapper.SerialNameAndProperties], plus filtering. */
@Serializable
data class ArgumentsKey(
  val productId: Int,
  val quantity: UInt,
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
          "io.sentry.tests.nav3.r8.Holder.PrivateNestedObject",
        ),
        Expectation(
          "private nested class",
          PrivateNestedClass("1"),
          "io.sentry.tests.nav3.r8.Holder.PrivateNestedClass",
          mapOf("id" to "1"),
        ),
      )
  }
}

private const val PKG = "io.sentry.tests.nav3.r8"

private val ID_ARGUMENTS = mapOf("id" to "1")

private val argumentsKey =
  ArgumentsKey(
    productId = 42,
    quantity = UInt.MAX_VALUE,
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
    "quantity" to 4_294_967_295L,
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

fun serializableExpectations(): List<Expectation> =
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
    Expectation(
      "named companion without rules",
      NamedCompanionKey.of("1"),
      "$PKG.NamedCompanionKey",
      ID_ARGUMENTS,
      alsoAcceptsUnnamed = !BuildConfig.KOTLINX_KEEPS_NAMED_COMPANIONS,
    ),
    Expectation(
      "named companion with rules",
      NamedCompanionKeyWithRules.of("1"),
      "$PKG.NamedCompanionKeyWithRules",
      ID_ARGUMENTS,
    ),
  ) + Holder.keys()

fun minificationChecks(): List<MinificationCheck> =
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
