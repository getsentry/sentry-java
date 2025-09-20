package io.sentry.android.distribution

import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.UpdateInfo
import io.sentry.UpdateStatus
import org.json.JSONException
import org.json.JSONObject

/** Parser for distribution API responses. */
internal class UpdateResponseParser(private val options: SentryOptions) {

  /**
   * Parses the API response and returns the appropriate UpdateStatus.
   *
   * @param statusCode HTTP status code
   * @param responseBody Response body as string
   * @return UpdateStatus indicating the result
   */
  fun parseResponse(statusCode: Int, responseBody: String): UpdateStatus {
    return when (statusCode) {
      200 -> parseSuccessResponse(responseBody)
      in 400..499 -> UpdateStatus.UpdateError("Client error: $statusCode")
      in 500..599 -> UpdateStatus.UpdateError("Server error: $statusCode")
      else -> UpdateStatus.UpdateError("Unexpected response code: $statusCode")
    }
  }

  private fun parseSuccessResponse(responseBody: String): UpdateStatus {
    return try {
      val json = JSONObject(responseBody)

      options.logger.log(SentryLevel.DEBUG, "Parsing distribution API response")

      // Check if there's a new release available
      val updateAvailable = json.optBoolean("updateAvailable", false)

      if (updateAvailable) {
        val updateInfo = parseUpdateInfo(json)
        UpdateStatus.NewRelease(updateInfo)
      } else {
        UpdateStatus.UpToDate.getInstance()
      }
    } catch (e: JSONException) {
      options.logger.log(SentryLevel.ERROR, e, "Failed to parse API response")
      UpdateStatus.UpdateError("Invalid response format: ${e.message}")
    } catch (e: Exception) {
      options.logger.log(SentryLevel.ERROR, e, "Unexpected error parsing response")
      UpdateStatus.UpdateError("Failed to parse response: ${e.message}")
    }
  }

  private fun parseUpdateInfo(json: JSONObject): UpdateInfo {
    val id = json.optString("id", "")
    val buildVersion = json.optString("buildVersion", "")
    val buildNumber = json.optInt("buildNumber", 0)
    val downloadUrl = json.optString("downloadUrl", "")
    val appName = json.optString("appName", "")
    val createdDate = json.optString("createdDate", "")

    // Validate required fields
    if (id.isEmpty() || buildVersion.isEmpty() || downloadUrl.isEmpty()) {
      throw IllegalArgumentException("Missing required update information in API response")
    }

    return UpdateInfo(id, buildVersion, buildNumber, downloadUrl, appName, createdDate)
  }
}
