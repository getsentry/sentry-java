package io.sentry.tests.nav3.r8

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
 * An open polymorphic type, whose values the properties mapper records as `[Unencodable]`, even if
 * their runtime classes are `@Serializable`, as it doesn't have the app's `SerializersModule`.
 */
interface Referrer

@Serializable @SerialName("campaign") data class Campaign(val name: String) : Referrer

/**
 * Named and encoded by its custom serializer, which the plugin's companion `serializer()` returns.
 */
@Serializable(with = SlugSerializer::class) class Slug(val value: String)

object SlugSerializer : KSerializer<Slug> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("slug", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Slug) = encoder.encodeString(value.value)

  override fun deserialize(decoder: Decoder): Slug = Slug(decoder.decodeString())
}

/**
 * An enum whose constant has a body, so the constant is an instance of an anonymous subclass that
 * isn't `@Serializable` itself. The mappers look up the declaring enum instead.
 */
@Serializable
enum class TabWithBody {
  HOME {
    override fun icon(): String = "house"
  };

  abstract fun icon(): String
}

/** A value class used as a back stack key itself, which is boxed when passed as `Any`. */
@Serializable @JvmInline value class TabId(val id: String)

/**
 * Has a named companion object but no named-companion R8 rules of the app's own.
 * kotlinx.serialization 1.10.0+'s own rules keep what its reflective serializer lookup needs, so
 * the mappers must name it. The floor's rules don't, in R8 full and compat mode alike, so at the
 * floor the mappers must record it as `/unknown`. In between, it depends on the version and R8 mode
 * (e.g., 1.9.x's lookup survives in compat mode only), so either outcome is accepted. See
 * `namedCompanionOutcome` in `build.gradle.kts`.
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
  val referrer: Referrer,
  val note: String?,
  val label: CharSequence,
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
    referrer = Campaign("spring"),
    note = null,
    label = StringBuilder("sale"),
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
    "referrer" to "[Unencodable]",
    "note" to null,
    "label" to "sale",
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
    Expectation("class with a custom serializer", Slug("shoes"), "slug", mapOf("value" to "shoes")),
    Expectation(
      "enum constant with a body",
      TabWithBody.HOME,
      "$PKG.TabWithBody",
      mapOf("value" to "HOME"),
    ),
    Expectation("value class", TabId("feed"), "$PKG.TabId", mapOf("value" to "feed")),
    if (BuildConfig.NAMED_COMPANION_OUTCOME == "unknown") {
      Expectation(
        "named companion without rules (/unknown at the floor)",
        NamedCompanionKey.of("1"),
        null,
      )
    } else {
      Expectation(
        "named companion without rules",
        NamedCompanionKey.of("1"),
        "$PKG.NamedCompanionKey",
        ID_ARGUMENTS,
        alsoAcceptsUnnamed = BuildConfig.NAMED_COMPANION_OUTCOME == "either",
      )
    },
    Expectation(
      "named companion with rules",
      NamedCompanionKeyWithRules.of("1"),
      "$PKG.NamedCompanionKeyWithRules",
      ID_ARGUMENTS,
    ),
  ) + Holder.keys()

fun minificationChecks(): List<Check> =
  listOf(
    Check(
      "PublicObject was renamed (runtime class ${PublicObject::class.java.name})",
      PublicObject::class.java.name != "$PKG.PublicObject",
    ),
    // Argument names come from serializer descriptors, so they survive R8 renaming fields.
    Check(
      "ArgumentsKey fields were renamed " +
        "(fields ${ArgumentsKey::class.java.declaredFields.map { it.name }})",
      ArgumentsKey::class.java.declaredFields.none { it.name == "productId" },
    ),
  )
