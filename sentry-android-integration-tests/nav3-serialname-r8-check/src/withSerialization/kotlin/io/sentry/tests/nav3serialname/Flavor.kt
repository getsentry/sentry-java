package io.sentry.tests.nav3serialname

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        ),
      )
  }
}

private const val PKG = "io.sentry.tests.nav3serialname"

fun flavorExpectations(): List<Expectation> =
  listOf(
    Expectation("public data object", PublicObject, "$PKG.PublicObject"),
    Expectation("data class with @SerialName", NamedClass("1"), "Named"),
    Expectation("data class", DefaultNamedClass("1"), "$PKG.DefaultNamedClass"),
    Expectation("sealed child object", Sealed.Child, "$PKG.Sealed.Child"),
    Expectation("sealed leaf class", Sealed.Leaf("1"), "$PKG.Sealed.Leaf"),
    Expectation("serializable enum", SerializableEnum.A, "$PKG.SerializableEnum"),
    Expectation("generic class", Generic("x"), null),
    Expectation("private top-level object", PrivateTopLevelObject, "$PKG.PrivateTopLevelObject"),
    Expectation("private top-level class", PrivateTopLevelClass("1"), "$PKG.PrivateTopLevelClass"),
  ) + Holder.keys()

fun flavorMinificationChecks(): List<MinificationCheck> =
  listOf(
    MinificationCheck(
      "PublicObject was renamed (runtime class ${PublicObject::class.java.name})",
      PublicObject::class.java.name != "$PKG.PublicObject",
    )
  )
