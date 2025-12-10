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
  fun `json gets detected correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "[1, 2, 3]"
    val bytes = rawJson.toByteArray()
    val maxSize = bytes.size + 1

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, maxSize, logger)
    assertNotNull(body)
    assertNull(body.warnings)
    assertEquals(listOf(1.0, 2.0, 3.0), body.body)
  }

  @Test
  fun `partial json gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "[1, 2, 3"
    val bytes = rawJson.toByteArray()
    val maxSize = bytes.size

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, maxSize, logger)
    assertNotNull(body)
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.JSON_TRUNCATED), body.warnings)
    assertEquals(listOf(1.0, 2.0, 3.0), body.body)
  }

  @Test
  fun `json object gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = """{"name":"John","age":30,"active":true}"""
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
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

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
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

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("hello world", body.body)
  }

  @Test
  fun `json number value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "123.45"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals(123.45, body.body)
  }

  @Test
  fun `json boolean value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "true"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals(true, body.body)
  }

  @Test
  fun `json null value gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "null"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertNull(body.body)
  }

  @Test
  fun `invalid json returns warning`() {
    val logger = mock<ILogger>()
    val rawJson = "{invalid json"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
    assertNotNull(body)
    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    assertTrue(map.isEmpty())
    assertNull(body.warnings)
  }

  @Test
  fun `completely malformed json returns warning`() {
    val logger = mock<ILogger>()
    val rawJson = "not json at all"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "application/json", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertNull(body.body)
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.INVALID_JSON), body.warnings)
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
        bytes.size + 1,
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
        bytes.size + 1,
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
        bytes.size + 1,
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
        bytes.size + 1,
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
    val formData = "name=John&age=30"
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
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.TEXT_TRUNCATED), body.warnings)

    @Suppress("UNCHECKED_CAST") val map = body.body as Map<String, Any>
    assertEquals("John", map["name"])
    assertEquals("30", map["age"])
  }

  @Test
  fun `plain text gets parsed as string`() {
    val logger = mock<ILogger>()
    val text = "This is plain text content"
    val bytes = text.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "text/plain", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertNull(body.warnings)
    assertEquals(text, body.body)
  }

  @Test
  fun `plain text without content type gets parsed as string`() {
    val logger = mock<ILogger>()
    val text = "This is plain text content"
    val bytes = text.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, null, null, bytes.size + 1, logger)
    assertNotNull(body)
    assertNull(body.warnings)
    assertEquals(text, body.body)
  }

  @Test
  fun `partial plain text gets warning`() {
    val logger = mock<ILogger>()
    val text = "This is plain text content"
    val bytes = text.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "text/plain", null, bytes.size, logger)
    assertNotNull(body)
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.TEXT_TRUNCATED), body.warnings)
    assertEquals(text, body.body)
  }

  @Test
  fun `binary image content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(100) { it.toByte() }

    val body = NetworkBodyParser.fromBytes(bytes, "image/png", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 100 bytes, type: image/png]", body.body)
  }

  @Test
  fun `binary video content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(500) { it.toByte() }

    val body = NetworkBodyParser.fromBytes(bytes, "video/mp4", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 500 bytes, type: video/mp4]", body.body)
  }

  @Test
  fun `binary audio content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(200) { it.toByte() }

    val body = NetworkBodyParser.fromBytes(bytes, "audio/mpeg", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 200 bytes, type: audio/mpeg]", body.body)
  }

  @Test
  fun `binary pdf content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(1000) { it.toByte() }

    val body = NetworkBodyParser.fromBytes(bytes, "application/pdf", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 1000 bytes, type: application/pdf]", body.body)
  }

  @Test
  fun `binary zip content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(300) { it.toByte() }

    val body = NetworkBodyParser.fromBytes(bytes, "application/zip", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 300 bytes, type: application/zip]", body.body)
  }

  @Test
  fun `binary gzip content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(150) { it.toByte() }

    val body = NetworkBodyParser.fromBytes(bytes, "application/gzip", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 150 bytes, type: application/gzip]", body.body)
  }

  @Test
  fun `binary octet-stream content returns description`() {
    val logger = mock<ILogger>()
    val bytes = ByteArray(250) { it.toByte() }

    val body =
      NetworkBodyParser.fromBytes(bytes, "application/octet-stream", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals("[Binary data, 250 bytes, type: application/octet-stream]", body.body)
  }

  @Test
  fun `content type is case insensitive`() {
    val logger = mock<ILogger>()
    val rawJson = "[1, 2, 3]"
    val bytes = rawJson.toByteArray()

    val body = NetworkBodyParser.fromBytes(bytes, "APPLICATION/JSON", null, bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals(listOf(1.0, 2.0, 3.0), body.body)
  }

  @Test
  fun `content type with charset parameter gets parsed correctly`() {
    val logger = mock<ILogger>()
    val rawJson = "[1, 2, 3]"
    val bytes = rawJson.toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "application/json; charset=UTF-8",
        null,
        bytes.size + 1,
        logger,
      )
    assertNotNull(body)
    assertEquals(listOf(1.0, 2.0, 3.0), body.body)
  }

  @Test
  fun `custom charset gets used for decoding`() {
    val logger = mock<ILogger>()
    val text = "Hello World"
    val bytes = text.toByteArray(Charsets.UTF_16)

    val body = NetworkBodyParser.fromBytes(bytes, "text/plain", "UTF-16", bytes.size + 1, logger)
    assertNotNull(body)
    assertEquals(text, body.body)
  }

  @Test
  fun `invalid charset returns error warning`() {
    val logger = mock<ILogger>()
    val bytes = "test".toByteArray()

    val body =
      NetworkBodyParser.fromBytes(
        bytes,
        "text/plain",
        "INVALID-CHARSET-NAME",
        bytes.size + 1,
        logger,
      )
    assertNotNull(body)
    assertTrue(body.body is String)
    assertTrue((body.body as String).contains("Failed to decode bytes"))
    assertEquals(listOf(NetworkBody.NetworkBodyWarning.BODY_PARSE_ERROR), body.warnings)
  }
}
