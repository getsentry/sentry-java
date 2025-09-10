package io.sentry.android.distribution.internal

import io.sentry.android.distribution.UpdateInfo
import org.json.JSONException
import org.json.JSONObject

/**
 * Response model for the Sentry preprod artifacts check-for-updates API.
 *
 * Based on the Sentry API response format: { "current": { ... }, "update": { ... } }
 */
internal data class CheckForUpdatesResponse(
  val current: InstallableBuildDetails?,
  val update: InstallableBuildDetails?,
)

/** Model representing build details from the API response. */
internal data class InstallableBuildDetails(
  val id: String,
  val buildVersion: String,
  val buildNumber: Int,
  val downloadUrl: String,
  val appName: String,
  val createdDate: String,
)

/** Converts InstallableBuildDetails to public UpdateInfo. */
internal fun InstallableBuildDetails.toUpdateInfo(): UpdateInfo {
  return UpdateInfo(
    id = id,
    buildVersion = buildVersion,
    buildNumber = buildNumber,
    downloadUrl = downloadUrl,
    appName = appName,
    createdDate = createdDate,
  )
}

/**
 * Parse JSON response from the check-for-updates API.
 *
 * @param jsonString The JSON response string
 * @return Parsed CheckForUpdatesResponse or null if parsing fails
 */
internal fun parseCheckForUpdatesResponse(jsonString: String?): CheckForUpdatesResponse? {
  if (jsonString.isNullOrBlank()) return null

  return try {
    val jsonObject = JSONObject(jsonString)

    val current = jsonObject.optJSONObject("current")?.let { parseInstallableBuildDetails(it) }
    val update = jsonObject.optJSONObject("update")?.let { parseInstallableBuildDetails(it) }

    CheckForUpdatesResponse(current, update)
  } catch (e: JSONException) {
    null
  }
}

/** Parse InstallableBuildDetails from JSON object. */
private fun parseInstallableBuildDetails(jsonObject: JSONObject): InstallableBuildDetails? {
  return try {
    InstallableBuildDetails(
      id = jsonObject.getString("id"),
      buildVersion = jsonObject.getString("build_version"),
      buildNumber = jsonObject.getInt("build_number"),
      downloadUrl = jsonObject.getString("download_url"),
      appName = jsonObject.getString("app_name"),
      createdDate = jsonObject.getString("created_date"),
    )
  } catch (e: JSONException) {
    null
  }
}
