package io.sentry.util.network

import io.sentry.ILogger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class NetworkBodyParserTest {

  @Test
  fun `null body gets detected correctly`() {
    val logger = mock<ILogger>()

    assertNull(NetworkBodyParser.fromBytes(null, "application/json", null, 512, logger))
    assertNull(NetworkBodyParser.fromBytes(ByteArray(0), "application/json", null, 512, logger))
  }

  @Test
  fun `null json body gets detected correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "null"
    val bytes = rawJson.toByteArray()
    val maxSize = bytes.size

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, maxSize, logger)
    assertNotNull(body)
    assertNull(body.warnings)
    assertNull(body.body)
  }

  @Test
  fun `json gets detected correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "[1, 2, 3]"
    val bytes = rawJson.toByteArray()
    val maxSize = bytes.size

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, maxSize, logger)
    assertNotNull(body)
    assertNull(body.warnings)
    assertEquals(listOf(1.0, 2.0, 3.0), body.body)
  }

  @Test
  fun `partial json gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "[1, 2, 3]"
    val bytes = rawJson.toByteArray()
    val maxSize = bytes.size

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, maxSize - 1, logger)
    assertNotNull(body)
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.JSON_TRUNCATED), body.warnings)
    assertEquals(listOf(1.0, 2.0, 3.0), body.body)
  }

  @Test
  fun `json object gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = """{"name":"John","age":30,"active":true}"""
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
    assertNotNull(body)
    assertNull(body.warnings)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    assertEquals("John", map["name"])
    assertEquals(30.0, map["age"])
    assertEquals(true, map["active"])
  }

  @Test
  fun `json with different data types gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson =
      """{"string":"text","number":42,"bool":false,"null":null,"array":[1,2],"nested":{"key":"value"}}"""
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
    assertNotNull(body)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any?>
    assertEquals("text", map["string"])
    assertEquals(42.0, map["number"])
    assertEquals(false, map["bool"])
    assertNull(map["null"])
    assertEquals(listOf(1.0, 2.0), map["array"])

    @Suppress("UNCHECKED_CAST") val nested = map["nested"] as Map<String, Any>
    assertEquals("value", nested["key"])
  }

  @Test
  fun `json string value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "\"hello world\""
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
    assertNotNull(body)
    assertEquals("hello world", body.body)
  }

  @Test
  fun `json number value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "123.45"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
    assertNotNull(body)
    assertEquals(123.45, body.body)
  }

  @Test
  fun `json boolean value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "true"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
    assertNotNull(body)
    assertEquals(true, body.body)
  }

  @Test
  fun `json null value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "null"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
    assertNotNull(body)
    assertNull(body.body)
  }

  @Test
  fun `completely malformed json returns warning`() {
    val logger = mock<ILogger>()
    val rawJson = "not json at all"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size, logger)
    assertNotNull(body)
    assertNull(body.body)
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.INVALID_JSON), body.warnings)
  }

  @Test
  fun `highly nested json gets truncated`() {
    val logger = mock<ILogger>()
    fun wrap(json: String) = "{\"key\": $json}"

    var rawJson = "{\"key\": \"value\"}"
    for (i in 0..200) {
      rawJson = wrap(rawJson)
    }

    val bytes = rawJson.toByteArray()
    val maxSize = bytes.size

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, maxSize, logger)
    assertNotNull(body)

    var map = body.body
    var depth = 0
    while (map is Map<*, *>) {
      depth++
      map = map["key"]
    }

    assertEquals(listOf(NetworkBody.NetworkBodyWarning.JSON_TRUNCATED), body.warnings)
    assertEquals(100, depth)
  }

  @Test
  fun `form urlencoded gets parsed correctly`() {
    val logger = mock<ILogger>()
    val formData = "name=John&age=30&city=NewYork"
    val bytes = formData.toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "application/x-www-form-urlencoded",
        null,
        bytes.size,
        logger,
      )
    assertNotNull(body)
    assertNull(body.warnings)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    assertEquals("John", map["name"])
    assertEquals("30", map["age"])
    assertEquals("NewYork", map["city"])
  }

  @Test
  fun `form urlencoded with special characters gets decoded correctly`() {
    val logger = mock<ILogger>()
    val formData = "message=Hello+World&email=test%40example.com"
    val bytes = formData.toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "application/x-www-form-urlencoded",
        null,
        bytes.size,
        logger,
      )
    assertNotNull(body)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    assertEquals("Hello World", map["message"])
    assertEquals("test@example.com", map["email"])
  }

  @Test
  fun `form urlencoded with multiple values for same key gets parsed as list`() {
    val logger = mock<ILogger>()
    val formData = "tag=java&tag=kotlin&tag=android"
    val bytes = formData.toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "application/x-www-form-urlencoded",
        null,
        bytes.size,
        logger,
      )
    assertNotNull(body)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    val tags = map["tag"] as List<String>
    assertEquals(listOf("java", "kotlin", "android"), tags)
  }

  @Test
  fun `form urlencoded with empty value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val formData = "key1=value1&key2=&key3=value3"
    val bytes = formData.toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "application/x-www-form-urlencoded",
        null,
        bytes.size,
        logger,
      )
    assertNotNull(body)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    assertEquals("value1", map["key1"])
    assertEquals("", map["key2"])
    assertEquals("value3", map["key3"])
  }

  @Test
  fun `partial form urlencoded gets parsed with warning`() {
    val logger = mock<ILogger>()
    val suffix = "&more=more"
    val formData = "name=John&age=30$suffix"
    val bytes = formData.toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "application/x-www-form-urlencoded",
        null,
        bytes.size - suffix.toByteArray().size,
        logger,
      )
    assertNotNull(body)
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.TEXT_TRUNCATED), body.warnings)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    assertEquals(2, map.size)
    assertEquals("John", map["name"])
    assertEquals("30", map["age"])
  }

  @Test
  fun `plain text gets parsed as string`() {
    val logger = mock<ILogger>()
    val text = "This is plain text content"
    val bytes = text.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "text/plain", null, bytes.size, logger)
    assertNotNull(body)
    assertNull(body.warnings)
    assertEquals(text, body.body)
  }

  @Test
  fun `plain text without content type gets parsed as string`() {
    val logger = mock<ILogger>()
    val text = "This is plain text content"
    val bytes = text.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, null, null, bytes.size, logger)
    assertNotNull(body)
    assertNull(body.warnings)
    assertEquals(text, body.body)
  }

  @Test
  fun `partial plain text gets warning`() {
    val logger = mock<ILogger>()
    val truncated = "..."
    val text = "This is plain text content"
    val fullText = "$text$truncated"
    val bytes = fullText.toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "text/plain",
        null,
        bytes.size - truncated.toByteArray().size,
        logger,
      )
    assertNotNull(body)
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.TEXT_TRUNCATED), body.warnings)
    assertEquals(text, body.body)
  }

  @Test
  fun `binary content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(100) { it.toByte() }

    val body = NetworkBodyParser.fromBytes(bytes, "image/png", null, bytes.size, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 100 bytes, type: image/png]", body.body)
  }

  @Test
  fun `custom charset gets used for decoding`() {
    val logger = mock<ILogger>()
    val text = "Hello World"
    val bytes = text.toByteArray(Charsets.UTF_16)

    val body = NetworkBodyParser.fromBytes(bytes, "text/plain", "UTF-16", bytes.size, logger)
    assertNotNull(body)
    assertEquals(text, body.body)
  }

  @Test
  fun `invalid charset returns error warning`() {
    val logger = mock<ILogger>()
    val bytes = "test".toByteArray()

    val body =
      NetworkBodyParser.fromBytes(bytes, "text/plain", "INVALID-CHARSET-NAME", bytes.size, logger)
    assertNotNull(body)
    assertTrue(body.body is String)
    assertTrue((body.body as String).contains("Failed to decode bytes"))
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.BODY_PARSE_ERROR), body.warnings)
  }
}
