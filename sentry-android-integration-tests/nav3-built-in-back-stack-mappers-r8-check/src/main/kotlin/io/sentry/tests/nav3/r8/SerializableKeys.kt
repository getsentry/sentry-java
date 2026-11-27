package io.sentry.tests.nav3.r8

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

/** Named by its custom serializer, which the plugin's companion `serializer()` returns. */
@Serializable(with = SlugSerializer::class) class Slug(val value: String)

object SlugSerializer : KSerializer<Slug> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("slug", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Slug) = encoder.encodeString(value.value)

  override fun deserialize(decoder: Decoder): Slug = Slug(decoder.decodeString())
}

/**
 * An enum whose constant has a body, so the constant is an instance of an anonymous subclass that
 * isn't `@Serializable` itself. The mapper looks up the declaring enum instead.
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
 * Has a named companion and no app keep rules for it. kotlinx.serialization 1.10.0+ supplies the
 * rules needed for serializer lookup, so the mapper must return its name. Older releases may return
 * the name or `/unknown`; `/unknown` must report `serializer_not_found`. See
 * `NAMED_COMPANION_ALWAYS_NAMED` in `build.gradle.kts`.
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
        ),
      )
  }
}

private const val PKG = "io.sentry.tests.nav3.r8"

fun serializableExpectations(): List<Expectation> =
  listOf(
    Expectation("public data object", PublicObject, "$PKG.PublicObject"),
    Expectation("data class with @SerialName", NamedClass("1"), "Named"),
    Expectation("data class", DefaultNamedClass("1"), "$PKG.DefaultNamedClass"),
    Expectation("sealed child object", Sealed.Child, "$PKG.Sealed.Child"),
    Expectation("sealed leaf class", Sealed.Leaf("1"), "$PKG.Sealed.Leaf"),
    Expectation(
      "serializable enum",
      SerializableEnum.A,
      "$PKG.SerializableEnum",
    ),
    // In R8 full mode too, as AGP's default ProGuard files keep Signature and
    // kotlinx.serialization's rules keep @Serializable classes.
    Expectation("generic class", Generic("x"), null, setOf(GENERIC_ENTRY_TYPE)),
    Expectation("private top-level object", PrivateTopLevelObject, "$PKG.PrivateTopLevelObject"),
    Expectation(
      "private top-level class",
      PrivateTopLevelClass("1"),
      "$PKG.PrivateTopLevelClass",
    ),
    Expectation("class with a custom serializer", Slug("shoes"), "slug"),
    Expectation(
      "enum constant with a body",
      TabWithBody.HOME,
      "$PKG.TabWithBody",
    ),
    Expectation("value class", TabId("feed"), "$PKG.TabId"),
    Expectation(
      "named companion without rules",
      NamedCompanionKey.of("1"),
      "$PKG.NamedCompanionKey",
      if (BuildConfig.NAMED_COMPANION_ALWAYS_NAMED) emptySet() else setOf(SERIALIZER_NOT_FOUND),
    ),
    Expectation(
      "named companion with rules",
      NamedCompanionKeyWithRules.of("1"),
      "$PKG.NamedCompanionKeyWithRules",
    ),
  ) + Holder.keys()

fun minificationChecks(): List<Check> =
  listOf(
    Check(
      "PublicObject was renamed (runtime class ${PublicObject::class.java.name})",
      PublicObject::class.java.name != "$PKG.PublicObject",
    ),
  )
