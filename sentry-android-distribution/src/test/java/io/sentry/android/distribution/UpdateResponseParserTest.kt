package io.sentry.android.distribution

import io.sentry.SentryOptions
import io.sentry.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateResponseParserTest {

  private lateinit var options: SentryOptions
  private lateinit var parser: UpdateResponseParser

  @Before
  fun setUp() {
    options = SentryOptions()
    parser = UpdateResponseParser(options)
  }

  @Test
  fun `parseResponse returns NewRelease when update is available`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "id": "update-123",
        "buildVersion": "2.0.0",
        "buildNumber": 42,
        "downloadUrl": "https://example.com/download",
        "appName": "Test App",
        "createdDate": "2023-10-01T00:00:00Z"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return NewRelease", result is UpdateStatus.NewRelease)
    val updateInfo = (result as UpdateStatus.NewRelease).info
    assertEquals("update-123", updateInfo.id)
    assertEquals("2.0.0", updateInfo.buildVersion)
    assertEquals(42, updateInfo.buildNumber)
    assertEquals("https://example.com/download", updateInfo.downloadUrl)
    assertEquals("Test App", updateInfo.appName)
    assertEquals("2023-10-01T00:00:00Z", updateInfo.createdDate)
  }

  @Test
  fun `parseResponse returns UpToDate when no update is available`() {
    val responseBody =
      """
      {
        "updateAvailable": false
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpToDate", result is UpdateStatus.UpToDate)
  }

  @Test
  fun `parseResponse returns UpToDate when updateAvailable is missing`() {
    val responseBody =
      """
      {
        "someOtherField": "value"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpToDate", result is UpdateStatus.UpToDate)
  }

  @Test
  fun `parseResponse returns UpdateError for 4xx status codes`() {
    val result = parser.parseResponse(404, "Not found")

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertEquals("Client error: 404", error.message)
  }

  @Test
  fun `parseResponse returns UpdateError for 5xx status codes`() {
    val result = parser.parseResponse(500, "Internal server error")

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertEquals("Server error: 500", error.message)
  }

  @Test
  fun `parseResponse returns UpdateError for unexpected status codes`() {
    val result = parser.parseResponse(999, "Unknown status")

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertEquals("Unexpected response code: 999", error.message)
  }

  @Test
  fun `parseResponse returns UpdateError for invalid JSON`() {
    val result = parser.parseResponse(200, "invalid json {")

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention invalid format",
      error.message.startsWith("Invalid response format:"),
    )
  }

  @Test
  fun `parseResponse returns UpdateError when required fields are missing`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "buildVersion": "2.0.0"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention failed to parse",
      error.message.startsWith("Failed to parse response:"),
    )
  }

  @Test
  fun `parseResponse handles minimal valid update response`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "id": "update-123",
        "buildVersion": "2.0.0",
        "downloadUrl": "https://example.com/download"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return NewRelease", result is UpdateStatus.NewRelease)
    val updateInfo = (result as UpdateStatus.NewRelease).info
    assertEquals("update-123", updateInfo.id)
    assertEquals("2.0.0", updateInfo.buildVersion)
    assertEquals(0, updateInfo.buildNumber) // Default value
    assertEquals("https://example.com/download", updateInfo.downloadUrl)
    assertEquals("", updateInfo.appName) // Default value
    assertEquals("", updateInfo.createdDate) // Default value
  }

  @Test
  fun `parseResponse handles empty response body`() {
    val result = parser.parseResponse(200, "")

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention invalid format",
      error.message.startsWith("Invalid response format:"),
    )
  }

  @Test
  fun `parseResponse handles null values in JSON`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "id": null,
        "buildVersion": "2.0.0",
        "downloadUrl": "https://example.com/download"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention failed to parse",
      error.message.startsWith("Failed to parse response:"),
    )
  }

  @Test
  fun `parseResponse returns specific error message when id is missing`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "buildVersion": "2.0.0",
        "downloadUrl": "https://example.com/download"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention missing id field",
      error.message.contains("Missing required fields in API response: id"),
    )
  }

  @Test
  fun `parseResponse returns specific error message when buildVersion is missing`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "id": "update-123",
        "downloadUrl": "https://example.com/download"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention missing buildVersion field",
      error.message.contains("Missing required fields in API response: buildVersion"),
    )
  }

  @Test
  fun `parseResponse returns specific error message when downloadUrl is missing`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "id": "update-123",
        "buildVersion": "2.0.0"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention missing downloadUrl field",
      error.message.contains("Missing required fields in API response: downloadUrl"),
    )
  }

  @Test
  fun `parseResponse returns specific error message when multiple fields are missing`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "buildNumber": 42
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention all missing required fields",
      error.message.contains(
        "Missing required fields in API response: id, buildVersion, downloadUrl"
      ),
    )
  }

  @Test
  fun `parseResponse returns specific error message when field is null string`() {
    val responseBody =
      """
      {
        "updateAvailable": true,
        "id": "null",
        "buildVersion": "2.0.0",
        "downloadUrl": "https://example.com/download"
      }
    """
        .trimIndent()

    val result = parser.parseResponse(200, responseBody)

    assertTrue("Should return UpdateError", result is UpdateStatus.UpdateError)
    val error = result as UpdateStatus.UpdateError
    assertTrue(
      "Error message should mention missing id field when value is 'null' string",
      error.message.contains("Missing required fields in API response: id"),
    )
  }
}
