package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.util.UrlUtils
import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

class KotlinxPropertiesEncoderFilteringTest {

  @Serializable data class WithSensitiveProperty(val query: String, val password: String)

  @Serializable data class WithNullableSecret(val id: String, val password: String?)

  @Serializable data class WithSensitiveMapKey(val params: Map<String, String>)

  @Serializable
  data class WithSensitiveKeysInList(val nested: Map<String, List<Map<String, String>>>)

  @Serializable @JvmInline value class HeaderName(val name: String)

  @Serializable data class WithHeaderNameMapKeys(val headers: Map<HeaderName, String>)

  /** A value class whose underlying property's name (`key`) is sensitive. */
  @Serializable @JvmInline value class SensitiveHeaderName(val key: String)

  /** A map between two other properties, so tests can check that those are kept. */
  @Serializable
  data class WithSensitiveValueClassMapKeys(
    val id: String,
    val headers: Map<SensitiveHeaderName, String>,
    val page: Int,
  )

  /**
   * A value class whose underlying property's name (`name`) isn't sensitive, but that of the value
   * class it wraps is.
   */
  @Serializable @JvmInline value class OuterSensitiveHeaderName(val name: SensitiveHeaderName)

  @Serializable
  data class WithOuterSensitiveHeaderNameMapKeys(val headers: Map<OuterSensitiveHeaderName, String>)

  /** A value class whose underlying property's name (`key`) is sensitive. */
  @Serializable
  @JvmInline
  value class UnencodableSensitiveHeaderName(
    @Serializable(with = ThrowingSerializer::class) val key: String,
  )

  @Serializable
  data class WithUnencodableSensitiveMapEntries(
    val id: String,
    val headers:
      Map<UnencodableSensitiveHeaderName, @Serializable(with = ThrowingSerializer::class) String>,
  )

  @Serializable data class WithSensitiveValueClassMapValues(val params: Map<String, TokenRef>)

  /** A map key that encodes as a [SensitiveHeaderName] when [isSecret], or else as its [name]. */
  @Serializable(with = MixedHeaderSerializer::class)
  data class MixedHeader(val name: String, val isSecret: Boolean)

  object MixedHeaderSerializer : KSerializer<MixedHeader> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.MixedHeader", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MixedHeader) {
      if (value.isSecret) {
        encoder.encodeSerializableValue(
          SensitiveHeaderName.serializer(),
          SensitiveHeaderName(value.name),
        )
      } else {
        encoder.encodeString(value.name)
      }
    }

    override fun deserialize(decoder: Decoder): MixedHeader =
      MixedHeader(decoder.decodeString(), isSecret = false)
  }

  @Serializable data class WithMixedHeaderMapKeys(val headers: Map<MixedHeader, String>)

  @Serializable
  data class WithSerialNames(
    @SerialName("position") val side: String,
    @SerialName("apiKey") val label: String,
  )

  @Serializable @JvmInline value class TokenRef(val token: String)

  @Serializable data class WithValueClassList(val references: List<TokenRef>)

  /**
   * A value class whose underlying property's name (`ref`) isn't sensitive, but whose value's is.
   */
  @Serializable @JvmInline value class OuterTokenRef(val ref: TokenRef)

  @Serializable data class WithOuterTokenRef(val reference: OuterTokenRef)

  @Serializable data class WithNullableTokenRef(val reference: TokenRef?)

  /** A value class whose underlying property's name (`token`) is sensitive, and nullable. */
  @Serializable @JvmInline value class NullableTokenRef(val token: String?)

  @Serializable data class WithNullableTokenRefValue(val reference: NullableTokenRef)

  @Serializable @JvmInline internal value class CredentialsRef(val credentials: Credentials)

  @Serializable internal data class WithStructuredValueClass(val owner: CredentialsRef)

  @Serializable
  internal data class WithStructuredSensitiveMapKeys(val owners: Map<CredentialsRef, Int>)

  @Serializable data class WithLargeSecret(val id: String, val refreshTokens: List<String>)

  @Serializable data class WithLargeSecretMapValue(val params: Map<String, List<Int>>)

  @Serializable
  data class WithUnencodableSecret(
    val id: String,
    @Serializable(with = ThrowingSerializer::class) val authToken: String,
  )

  @Serializable
  data class WithUnencodableSecretMapValue(
    val params: Map<String, @Serializable(with = ThrowingSerializer::class) String>,
  )

  /** A value class whose underlying property's name (`token`) is sensitive. */
  @Serializable
  @JvmInline
  value class UnencodableSecretRef(
    @Serializable(with = ThrowingSerializer::class) val token: String,
  )

  @Serializable data class WithUnencodableSecretRef(val id: String, val ref: UnencodableSecretRef)

  @Serializable data class WithSensitiveTextProperty(val id: String, val authToken: CharSequence)

  @Serializable data class WithTextMapKeys(val params: Map<CharSequence, String>)

  /** A `CharSequence` whose text can't be read, failing the test if anything tries. */
  class UnreadableText : CharSequence {
    override val length: Int
      get() = error("length read")

    override fun get(index: Int): Char = error("character read")

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
      error("subsequence read")

    override fun toString(): String = error("text read")
  }

  @Serializable @JvmInline value class Wrapped(val raw: String)

  @Serializable
  data class SecretsProfile(val name: String, @SerialName("PASSWORD") val password: String)

  @Serializable data class SecretsItem(val sku: String, val apiKey: String)

  @Serializable
  sealed interface SecretsOrigin {
    @Serializable
    @SerialName("login")
    data class Login(val user: String, val authToken: String) : SecretsOrigin
  }

  /**
   * Places sensitive keys at the common structural positions: properties, nested and sealed
   * classes, lists, maps, and value classes. Structured map keys, open polymorphic values, and
   * `CharSequence` and value class map keys are covered by their own tests.
   */
  @Serializable
  internal data class SecretsEverywhere(
    val query: String,
    val authToken: String,
    val password: String?,
    val sort: Sort,
    val profile: SecretsProfile,
    val items: List<SecretsItem>,
    val headers: Map<String, String>,
    val filters: Map<String, Map<String, String>>,
    val rows: List<Map<String, String>>,
    val grid: List<List<Map<String, String>>>,
    val label: Wrapped,
    val sessionSecret: Wrapped,
    val origin: SecretsOrigin,
    val authOrigin: SecretsOrigin,
    val refreshTokens: List<String>,
    val credentials: Map<String, String>,
    val reference: TokenRef,
  )

  /** The arguments of a [WithSensitiveValueClassMapKeys] whose map is filtered as a whole. */
  private val filteredHeadersArguments =
    mapOf("id" to "1", "headers" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE, "page" to 2)

  // Sensitive property names and map keys

  @Test
  fun `encode() - filters the values of sensitive properties`() {
    val entry = WithSensitiveProperty(query = "shoes", password = "secret")

    val encoded = encodeToMap(WithSensitiveProperty.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("query" to "shoes", "password" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters a null sensitive property`() {
    val entry = WithNullableSecret(id = "1", password = null)

    val encoded = encodeToMap(WithNullableSecret.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "password" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters the values of properties whose serial name is sensitive`() {
    val entry = WithSerialNames(side = "left", label = "secret")

    val encoded = encodeToMap(WithSerialNames.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("position" to "left", "apiKey" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters the values of sensitive map keys`() {
    val entry = WithSensitiveMapKey(mapOf("query" to "shoes", "authToken" to "secret"))

    val encoded = encodeToMap(WithSensitiveMapKey.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "params" to mapOf("query" to "shoes", "authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
        ),
      )
  }

  @Test
  fun `encode() - filters the values of a top-level map's sensitive keys`() {
    val serializer = MapSerializer(String.serializer(), String.serializer())

    val encoded = encodeToMap(serializer, mapOf("query" to "shoes", "password" to "secret"))

    assertThat(encoded)
      .isEqualTo(mapOf("query" to "shoes", "password" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters the values of sensitive map keys in maps nested in lists`() {
    val entry =
      WithSensitiveKeysInList(
        nested = mapOf("items" to listOf(mapOf("authToken" to "secret", "query" to "shoes"))),
      )

    val encoded = encodeToMap(WithSensitiveKeysInList.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "nested" to
            mapOf(
              "items" to
                listOf(
                  mapOf("authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE, "query" to "shoes"),
                ),
            ),
        ),
      )
  }

  @Test
  fun `encode() - filters the values of value class map keys whose text is sensitive`() {
    val entry =
      WithHeaderNameMapKeys(
        mapOf(HeaderName("Accept") to "json", HeaderName("Authorization") to "Bearer abc123"),
      )

    val encoded = encodeToMap(WithHeaderNameMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "headers" to
            mapOf("Accept" to "json", "Authorization" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
        ),
      )
  }

  @Test
  fun `encode() - filters the values of CharSequence map keys whose text is sensitive`() {
    val entry =
      WithTextMapKeys(mapOf(StringBuilder("query") to "shoes", StringBuilder("token") to "secret"))

    val encoded = encodeToMap(WithTextMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("params" to mapOf("query" to "shoes", "token" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE)),
      )
  }

  @Test
  fun `encode() - filters a map value based only on its own key`() {
    val entry =
      WithMixedHeaderMapKeys(
        mapOf(
          MixedHeader("Accept", isSecret = false) to "VISIBLE-1",
          MixedHeader("Authorization", isSecret = true) to "LEAKED-SECRET-1",
          MixedHeader("Accept-Language", isSecret = false) to "VISIBLE-2",
        ),
      )

    val encoded = encodeToMap(WithMixedHeaderMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "headers" to
            mapOf(
              "Accept" to "VISIBLE-1",
              UrlUtils.SENSITIVE_DATA_SUBSTITUTE to UrlUtils.SENSITIVE_DATA_SUBSTITUTE,
              "Accept-Language" to "VISIBLE-2",
            ),
        ),
      )
  }

  @Test
  fun `encode() - drops the arguments for a duplicate key when two map keys are filtered by their serializer`() {
    val entry =
      WithMixedHeaderMapKeys(
        mapOf(
          MixedHeader("Authorization", isSecret = true) to "LEAKED-SECRET-1",
          MixedHeader("X-Api-Key", isSecret = true) to "LEAKED-SECRET-2",
        ),
      )

    assertDropsArguments(ArgumentDropReason.DUPLICATE_KEY) {
      encodeToMap(WithMixedHeaderMapKeys.serializer(), entry)
    }
  }

  // Security regression test: a filtered key must not overwrite or merge with a visible entry.
  @Test
  fun `encode() - drops the arguments for a duplicate key when a filtered map key matches an unfiltered key's text`() {
    val entry =
      WithMixedHeaderMapKeys(
        mapOf(
          MixedHeader(UrlUtils.SENSITIVE_DATA_SUBSTITUTE, isSecret = false) to "VISIBLE",
          MixedHeader("Authorization", isSecret = true) to "LEAKED-SECRET",
        ),
      )

    assertDropsArguments(ArgumentDropReason.DUPLICATE_KEY) {
      encodeToMap(WithMixedHeaderMapKeys.serializer(), entry)
    }
  }

  @Test
  fun `encode() - rejects a literal substitute colliding with a filtered pair in either order`() {
    val substitute = UrlUtils.SENSITIVE_DATA_SUBSTITUTE
    val entries =
      listOf(
        MixedHeader(substitute, isSecret = false) to substitute,
        MixedHeader("Authorization", isSecret = true) to "secret",
      )

    for (order in listOf(entries, entries.reversed())) {
      assertDropsArguments(ArgumentDropReason.DUPLICATE_KEY) {
        encodeToMap(WithMixedHeaderMapKeys.serializer(), WithMixedHeaderMapKeys(order.toMap()))
      }
    }
  }

  // Value classes whose underlying property is sensitive

  @Test
  fun `encode() - filters a top-level value class whose underlying property is sensitive under the value key`() {
    val encoded = encodeToMap(TokenRef.serializer(), TokenRef("abc123"))

    assertThat(encoded)
      .isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters list elements whose underlying property is sensitive`() {
    val entry = WithValueClassList(listOf(TokenRef("abc"), TokenRef("def")))

    val encoded = encodeToMap(WithValueClassList.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "references" to
            listOf(UrlUtils.SENSITIVE_DATA_SUBSTITUTE, UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
        ),
      )
  }

  @Test
  fun `encode() - filters map values whose underlying property is sensitive`() {
    val entry = WithSensitiveValueClassMapValues(mapOf("reference" to TokenRef("abc123")))

    val encoded = encodeToMap(WithSensitiveValueClassMapValues.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("params" to mapOf("reference" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE)))
  }

  @Test
  fun `encode() - filters a value class wrapping a class when its underlying property is sensitive`() {
    val entry =
      WithStructuredValueClass(CredentialsRef(Credentials(user = "jane", password = "secret")))

    val encoded = encodeToMap(WithStructuredValueClass.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("owner" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters a value class wrapping a value class whose underlying property is sensitive`() {
    val entry = WithOuterTokenRef(OuterTokenRef(TokenRef("abc123")))

    val encoded = encodeToMap(WithOuterTokenRef.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("reference" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters a value class whose nullable sensitive underlying property holds a value`() {
    val entry = WithNullableTokenRefValue(NullableTokenRef("abc123"))

    val encoded = encodeToMap(WithNullableTokenRefValue.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("reference" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters a value class whose nullable sensitive underlying property holds null`() {
    val entry = WithNullableTokenRefValue(NullableTokenRef(null))

    val encoded = encodeToMap(WithNullableTokenRefValue.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("reference" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  /** A null value class has no underlying property to check, and no value to leak. */
  @Test
  fun `encode() - encodes a null value class property whose underlying property is sensitive as null`() {
    val encoded = encodeToMap(WithNullableTokenRef.serializer(), WithNullableTokenRef(null))

    assertThat(encoded).isEqualTo(mapOf("reference" to null))
  }

  // Maps whose keys are value classes with a sensitive underlying property

  @Test
  fun `encode() - filters a map with sensitive value class keys as a whole, keeping the other properties`() {
    val entry =
      WithSensitiveValueClassMapKeys(
        id = "1",
        headers =
          mapOf(
            SensitiveHeaderName("Authorization") to "Bearer LEAKED-SECRET-1",
            SensitiveHeaderName("X-Api-Key") to "LEAKED-SECRET-2",
            // Filtered by its underlying property name even though its text isn't sensitive.
            SensitiveHeaderName("Accept") to "LEAKED-SECRET-3",
          ),
        page = 2,
      )

    val encoded = encodeToMap(WithSensitiveValueClassMapKeys.serializer(), entry)

    assertThat(encoded).isEqualTo(filteredHeadersArguments)
  }

  @Test
  fun `encode() - filters a map with one sensitive value class key as a whole`() {
    val entry =
      WithSensitiveValueClassMapKeys(
        id = "1",
        headers = mapOf(SensitiveHeaderName("Authorization") to "LEAKED-SECRET"),
        page = 2,
      )

    val encoded = encodeToMap(WithSensitiveValueClassMapKeys.serializer(), entry)

    assertThat(encoded).isEqualTo(filteredHeadersArguments)
  }

  @Test
  fun `encode() - filters an empty map with sensitive value class keys as a whole`() {
    val entry = WithSensitiveValueClassMapKeys(id = "1", headers = emptyMap(), page = 2)

    val encoded = encodeToMap(WithSensitiveValueClassMapKeys.serializer(), entry)

    assertThat(encoded).isEqualTo(filteredHeadersArguments)
  }

  @Test
  fun `encode() - filters a map with sensitive value class keys as a whole without iterating over it`() {
    val headers =
      IterationCountingMap((1..1_000).associate { SensitiveHeaderName("X-Header-$it") to "$it" })
    val entry = WithSensitiveValueClassMapKeys(id = "1", headers = headers, page = 2)

    val encoded = encodeToMap(WithSensitiveValueClassMapKeys.serializer(), entry)

    assertThat(encoded).isEqualTo(filteredHeadersArguments)
    assertThat(headers.entriesRead).isEqualTo(0)
  }

  @Test
  fun `encode() - filters a map whose value class keys wrap a value class with a sensitive underlying property`() {
    val entry =
      WithOuterSensitiveHeaderNameMapKeys(
        mapOf(OuterSensitiveHeaderName(SensitiveHeaderName("Accept")) to "LEAKED-SECRET"),
      )

    val encoded = encodeToMap(WithOuterSensitiveHeaderNameMapKeys.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("headers" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters a map whose sensitive value class keys wrap a class, without reporting it unencodable`() {
    var unencodableValueCount = 0
    val entry =
      WithStructuredSensitiveMapKeys(mapOf(CredentialsRef(Credentials("jane", "secret")) to 1))

    val encoded =
      encodeToMap(WithStructuredSensitiveMapKeys.serializer(), entry) { unencodableValueCount++ }

    assertThat(encoded).isEqualTo(mapOf("owners" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
    assertThat(unencodableValueCount).isEqualTo(0)
  }

  @Test
  fun `encode() - doesn't run the key or value serializers of a map with sensitive value class keys`() {
    val entry =
      WithUnencodableSensitiveMapEntries(
        id = "1",
        headers = mapOf(UnencodableSensitiveHeaderName("a") to "b"),
      )

    val encoded = encodeToMap(WithUnencodableSensitiveMapEntries.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "headers" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - doesn't report an unencodable value for a map with sensitive value class keys`() {
    var unencodableValueCount = 0
    val entry =
      WithSensitiveValueClassMapKeys(
        id = "1",
        headers = mapOf(SensitiveHeaderName("a") to "b", SensitiveHeaderName("c") to "d"),
        page = 2,
      )

    encodeToMap(WithSensitiveValueClassMapKeys.serializer(), entry) { unencodableValueCount++ }

    assertThat(unencodableValueCount).isEqualTo(0)
  }

  @Test
  fun `encode() - filters a top-level map with sensitive value class keys under the value key`() {
    val serializer = MapSerializer(SensitiveHeaderName.serializer(), String.serializer())

    val encoded =
      encodeToMap(serializer, mapOf(SensitiveHeaderName("Authorization") to "LEAKED-SECRET"))

    assertThat(encoded)
      .isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  // Work skipped for sensitive values

  @Test
  fun `encode() - counts a sensitive property as one value regardless of its size`() {
    val entry =
      WithLargeSecret(id = "1", refreshTokens = List(MAX_ARGUMENT_VALUES + 1) { "token$it" })

    val encoded = encodeToMap(WithLargeSecret.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "refreshTokens" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - counts a sensitive map value as one value regardless of its size`() {
    val entry =
      WithLargeSecretMapValue(
        params = mapOf("query" to listOf(1), "authToken" to List(MAX_ARGUMENT_VALUES + 1) { it }),
      )

    val encoded = encodeToMap(WithLargeSecretMapValue.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "params" to
            mapOf("query" to listOf(1), "authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
        ),
      )
  }

  @Test
  fun `encode() - doesn't run the serializers of sensitive properties`() {
    val entry = WithUnencodableSecret(id = "1", authToken = "secret")

    val encoded = encodeToMap(WithUnencodableSecret.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - filters a sensitive CharSequence property without reading its text`() {
    val entry = WithSensitiveTextProperty(id = "1", authToken = UnreadableText())

    val encoded = encodeToMap(WithSensitiveTextProperty.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  @Test
  fun `encode() - doesn't run the serializers of sensitive map values`() {
    val entry = WithUnencodableSecretMapValue(mapOf("authToken" to "secret"))

    val encoded = encodeToMap(WithUnencodableSecretMapValue.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("params" to mapOf("authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE)))
  }

  @Test
  fun `encode() - doesn't run the serializers wrapped by a value class with a sensitive underlying property`() {
    val entry = WithUnencodableSecretRef(id = "1", ref = UnencodableSecretRef("secret"))

    val encoded = encodeToMap(WithUnencodableSecretRef.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "ref" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
  }

  // Sensitive keys anywhere in the encoded tree

  /**
   * Scans the entire output for secrets without requiring a particular shape. This checks the
   * combined filtering result; it doesn't distinguish encoder checks from `SensitiveKeyFilter`.
   */
  @Test
  fun `encode() - doesn't record the values of sensitive keys anywhere in the encoded tree`() {
    val entry =
      SecretsEverywhere(
        query = "VISIBLE-1",
        authToken = "LEAKED-SECRET-1",
        password = "LEAKED-SECRET-2",
        sort = Sort.NEWEST,
        profile = SecretsProfile(name = "VISIBLE-2", password = "LEAKED-SECRET-3"),
        items =
          listOf(
            SecretsItem(sku = "VISIBLE-3", apiKey = "LEAKED-SECRET-4"),
            SecretsItem(sku = "VISIBLE-4", apiKey = "LEAKED-SECRET-5"),
          ),
        headers = mapOf("accept" to "VISIBLE-5", "X-Api-Key" to "LEAKED-SECRET-6"),
        filters = mapOf("color" to mapOf("shade" to "VISIBLE-6", "sessionId" to "LEAKED-SECRET-7")),
        rows = listOf(mapOf("note" to "VISIBLE-7", "Bearer" to "LEAKED-SECRET-8")),
        grid = listOf(listOf(mapOf("cell" to "VISIBLE-8", "jwt" to "LEAKED-SECRET-9"))),
        label = Wrapped("VISIBLE-9"),
        sessionSecret = Wrapped("LEAKED-SECRET-10"),
        origin = SecretsOrigin.Login(user = "VISIBLE-10", authToken = "LEAKED-SECRET-11"),
        authOrigin = SecretsOrigin.Login(user = "LEAKED-SECRET-12", authToken = "LEAKED-SECRET-13"),
        refreshTokens = listOf("LEAKED-SECRET-14", "LEAKED-SECRET-15"),
        credentials = mapOf("user" to "LEAKED-SECRET-16"),
        reference = TokenRef("LEAKED-SECRET-17"),
      )

    val leaves = leavesOf(encodeToMap(SecretsEverywhere.serializer(), entry))

    assertThat(leaves.filterValues { it.contains("LEAKED-SECRET") }).isEmpty()
    assertThat(leaves.values).containsAtLeastElementsIn((1..10).map { "VISIBLE-$it" } + "NEWEST")
    // authToken, password, PASSWORD, apiKey (x2), X-Api-Key, sessionId, Bearer, jwt,
    // sessionSecret, origin's authToken, authOrigin, refreshTokens, credentials, and reference
    // (whose value class wraps a token).
    assertThat(leaves.values.count { it == UrlUtils.SENSITIVE_DATA_SUBSTITUTE }).isEqualTo(15)
  }

  /**
   * Flattens [value] into its map keys and scalar values, stringified and keyed by their path, so
   * that a failing assertion names where each one sits.
   */
  private fun leavesOf(
    value: Any?,
    path: String = "",
    leaves: MutableMap<String, String> = LinkedHashMap(),
  ): Map<String, String> {
    when (value) {
      is Map<*, *> ->
        value.forEach { (key, child) ->
          leaves["$path.$key (key)"] = key.toString()
          leavesOf(child, "$path.$key", leaves)
        }
      is Iterable<*> ->
        value.forEachIndexed { index, child -> leavesOf(child, "$path[$index]", leaves) }
      else -> leaves[path] = value.toString()
    }
    return leaves
  }
}
