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

      // Check if there's an update object in the response
      val updateJson = json.optJSONObject("update")

      if (updateJson != null) {
        val updateInfo = parseUpdateInfo(updateJson)
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
    val buildVersion = json.optString("build_version", "")
    val buildNumber = json.optInt("build_number", 0)
    val downloadUrl = json.optString("download_url", "")
    val appName = json.optString("app_name", "")
    val createdDate = json.optString("created_date", "")

    // Validate required fields (optString returns "null" for null values)
    val missingFields = mutableListOf<String>()

    if (id.isEmpty() || id == "null") {
      missingFields.add("id")
    }
    if (buildVersion.isEmpty() || buildVersion == "null") {
      missingFields.add("buildVersion")
    }
    if (downloadUrl.isEmpty() || downloadUrl == "null") {
      missingFields.add("downloadUrl")
    }

    if (missingFields.isNotEmpty()) {
      throw IllegalArgumentException(
        "Missing required fields in API response: ${missingFields.joinToString(", ")}"
      )
    }

    return UpdateInfo(id, buildVersion, buildNumber, downloadUrl, appName, createdDate)
  }
}
