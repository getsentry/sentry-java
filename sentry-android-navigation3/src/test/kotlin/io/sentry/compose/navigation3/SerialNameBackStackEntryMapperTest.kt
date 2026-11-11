package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SerialNameBackStackEntryMapperTest {

  @Serializable @SerialName("Home") data object Home

  @Serializable @SerialName("Detail") data class Detail(val id: String)

  data class NotSerializable(val id: String)

  private class Fixture {
    val logger = mock<ILogger>()
    val lookupCalls = mutableListOf<Class<*>>()
    val argumentCalls = mutableListOf<Any>()
    val names = mapOf<Class<*>, String>(Home::class.java to "Home", Detail::class.java to "Detail")
    var arguments: (Any) -> Map<String, Any?>? = { emptyMap() }

    fun getSut(isSerializationAvailable: Boolean = true, includeArguments: Boolean = false) =
      SerialNameBackStackEntryMapper(
        lookup =
          if (isSerializationAvailable) {
            object : SerialNameLookup {
              override fun serialNameOrNull(type: Class<*>): String? {
                lookupCalls += type
                return names[type]
              }

              override fun argumentsOrNull(backStackEntry: Any): Map<String, Any?>? {
                argumentCalls += backStackEntry
                return arguments(backStackEntry)
              }
            }
          } else {
            null
          },
        includeArguments = includeArguments,
        logger = { logger },
      )
  }

  private val fixture = Fixture()

  @Test
  fun `names entries after their serial names without arguments`() {
    val sut = fixture.getSut()

    val mapped = sut.map(Detail("secret-id"))

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Detail"))
    assertThat(mapped?.arguments).isNull()
  }

  @Test
  fun `returns null when the serial name can't be resolved`() {
    val sut = fixture.getSut()

    assertThat(sut.map(NotSerializable("1"))).isNull()
  }

  @Test
  fun `returns null when kotlinx serialization is unavailable`() {
    val sut = fixture.getSut(isSerializationAvailable = false)

    assertThat(sut.map(Home)).isNull()
  }

  @Test
  fun `looks up each class once, including failed lookups`() {
    val sut = fixture.getSut()

    repeat(3) {
      sut.map(Home)
      sut.map(Detail("$it"))
      sut.map(NotSerializable("$it"))
    }

    assertThat(fixture.lookupCalls)
      .containsExactly(Home::class.java, Detail::class.java, NotSerializable::class.java)
  }

  @Test
  fun `logs a warning once per unresolvable class`() {
    val sut = fixture.getSut()

    repeat(3) {
      sut.map(NotSerializable("$it"))
      sut.map(Home)
    }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<String>(),
        eq(NotSerializable::class.java.name),
        eq(
          "the type is not @Serializable or its serializer can't be looked up without type arguments"
        ),
      )
    verify(fixture.logger, never()).log(any(), any<String>(), eq(Home::class.java.name), any())
  }

  @Test
  fun `logs that kotlinx serialization is unavailable once per class`() {
    val sut = fixture.getSut(isSerializationAvailable = false)

    repeat(3) {
      sut.map(Home)
      sut.map(Detail("$it"))
    }

    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<String>(),
        eq(Home::class.java.name),
        eq("kotlinx.serialization is not available"),
      )
    verify(fixture.logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        any<String>(),
        eq(Detail::class.java.name),
        eq("kotlinx.serialization is not available"),
      )
  }

  @Test
  fun `serialName returns a shared mapper backed by kotlinx serialization`() {
    val sut = BackStackEntryMapper.serialName()

    assertThat(sut).isSameInstanceAs(BackStackEntryMapper.serialName())
    assertThat(sut.map(Home)).isEqualTo(SentryBackStackEntry("Home"))
    assertThat(sut.map(Detail("1"))).isEqualTo(SentryBackStackEntry("Detail"))
    assertThat(sut.map(NotSerializable("1"))).isNull()
  }

  @Test
  fun `serialName can serve as the mapper for any entry type`() {
    val homeMapper: BackStackEntryMapper<Home> = BackStackEntryMapper.serialName()

    assertThat(homeMapper.map(Home)).isEqualTo(SentryBackStackEntry("Home"))
  }

  @Test
  fun `custom mappers can delegate unhandled entries to serialName`() {
    val sut =
      BackStackEntryMapper<Any> { entry ->
        when (entry) {
          is Detail -> SentryBackStackEntry("Detail", mapOf("id" to entry.id))
          else -> BackStackEntryMapper.serialName().map(entry)
        }
      }

    assertThat(sut.map(Detail("1"))).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "1")))
    assertThat(sut.map(Home)).isEqualTo(SentryBackStackEntry("Home"))
  }

  @Test
  fun `doesn't encode arguments unless asked to`() {
    val sut = fixture.getSut()

    sut.map(Detail("1"))

    assertThat(fixture.argumentCalls).isEmpty()
  }

  @Test
  fun `records encoded arguments when asked to`() {
    fixture.arguments = { mapOf("id" to (it as Detail).id) }
    val sut = fixture.getSut(includeArguments = true)

    assertThat(sut.map(Detail("1"))).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "1")))
    assertThat(sut.map(Detail("2"))).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "2")))
  }

  @Test
  fun `filters sensitive values at every level of nesting`() {
    fixture.arguments = {
      mapOf(
        "id" to "1",
        "authToken" to "secret",
        "nested" to mapOf("Password" to "secret", "query" to "shoes"),
        "items" to listOf(mapOf("sessionId" to "secret", "sku" to "A1")),
        "apiKeys" to listOf("secret"),
      )
    }
    val sut = fixture.getSut(includeArguments = true)

    assertThat(sut.map(Detail("1"))?.arguments)
      .isEqualTo(
        mapOf(
          "id" to "1",
          "authToken" to "[Filtered]",
          "nested" to mapOf("Password" to "[Filtered]", "query" to "shoes"),
          "items" to listOf(mapOf("sessionId" to "[Filtered]", "sku" to "A1")),
          "apiKeys" to "[Filtered]",
        )
      )
  }

  @Test
  fun `records no arguments for entries without properties`() {
    fixture.arguments = { emptyMap() }
    val sut = fixture.getSut(includeArguments = true)

    val mapped = sut.map(Home)

    assertThat(mapped).isEqualTo(SentryBackStackEntry("Home"))
    assertThat(mapped?.arguments).isNull()
  }

  @Test
  fun `keeps the name and logs once per class when arguments can't be encoded`() {
    fixture.arguments = { null }
    val sut = fixture.getSut(includeArguments = true)

    repeat(3) {
      assertThat(sut.map(Detail("$it"))).isEqualTo(SentryBackStackEntry("Detail"))
    }

    verify(fixture.logger, times(1))
      .log(eq(SentryLevel.WARNING), any<String>(), eq(Detail::class.java.name))
  }

  @Test
  fun `doesn't encode arguments for entries it can't name`() {
    val sut = fixture.getSut(includeArguments = true)

    assertThat(sut.map(NotSerializable("1"))).isNull()
    assertThat(fixture.argumentCalls).isEmpty()
  }

  @Test
  fun `serialNameWithArguments returns a shared mapper backed by kotlinx serialization`() {
    val sut = BackStackEntryMapper.serialNameWithArguments()

    assertThat(sut).isSameInstanceAs(BackStackEntryMapper.serialNameWithArguments())
    assertThat(sut).isNotSameInstanceAs(BackStackEntryMapper.serialName())
    assertThat(sut.map(Home)).isEqualTo(SentryBackStackEntry("Home"))
    assertThat(sut.map(Detail("1"))).isEqualTo(SentryBackStackEntry("Detail", mapOf("id" to "1")))
    assertThat(sut.map(NotSerializable("1"))).isNull()
  }
}
