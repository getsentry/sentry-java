package io.sentry.android.distribution.internal

import android.content.Context
import io.sentry.SentryOptions
import io.sentry.android.distribution.DistributionOptions
import io.sentry.android.distribution.UpdateStatus
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Internal implementation for build distribution functionality. */
internal object DistributionInternal {
  private var options: DistributionOptions? = null
  private var httpClient: SentryHttpClient? = null
  private var executor: Executor? = null
  private var isInitialized = false

  @Synchronized
  fun init(context: Context, distributionOptions: DistributionOptions) {
    if (isInitialized) {
      // TODO: Add logging when Sentry logger is available
      return
    }

    options = distributionOptions

    // Create HTTP client with Sentry options (use defaults if none available)
    val sentryOptions = SentryOptions()
    httpClient = SentryHttpClient(sentryOptions, distributionOptions.orgAuthToken)

    // Create single-threaded executor for background tasks
    executor =
      Executors.newSingleThreadExecutor { r ->
        Thread(r, "SentryDistribution").apply { isDaemon = true }
      }

    isInitialized = true
  }

  fun isEnabled(): Boolean {
    return isInitialized && options != null && httpClient != null
  }

  fun checkForUpdate(context: Context): UpdateStatus {
    // Convert to blocking call since we can't use coroutines
    val future = checkForUpdateCompletableFuture(context)
    return try {
      future.get()
    } catch (e: Exception) {
      UpdateStatus.Error("Error checking for updates: ${e.message}")
    }
  }

  fun checkForUpdateCompletableFuture(context: Context): CompletableFuture<UpdateStatus> {
    val future = CompletableFuture<UpdateStatus>()
    val currentExecutor = executor

    if (!isEnabled() || currentExecutor == null) {
      future.complete(UpdateStatus.Error("Distribution not initialized"))
      return future
    }

    // Execute on background thread
    currentExecutor.execute {
      try {
        val result = performUpdateCheck(context)
        future.complete(result)
      } catch (e: Exception) {
        future.complete(UpdateStatus.Error("Error checking for updates: ${e.message}"))
      }
    }

    return future
  }

  /** Perform the actual update check (runs on background thread). */
  private fun performUpdateCheck(context: Context): UpdateStatus {
    val currentOptions = options
    val currentHttpClient = httpClient

    if (currentOptions == null || currentHttpClient == null) {
      return UpdateStatus.Error("Distribution not initialized")
    }

    try {
      // Get app metadata
      val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
      val appId = context.packageName
      val version = packageInfo.versionName ?: "unknown"
      val binaryIdentifier = getBinaryIdentifier(context) ?: "unknown"

      println("DistributionInternal: App metadata:")
      println("  Package name: $appId")
      println("  Version: $version")
      println("  Binary identifier: $binaryIdentifier")
      println("  Organization slug: ${currentOptions.organizationSlug}")
      println("  Project slug: ${currentOptions.projectSlug}")
      println("  Base URL: ${currentOptions.sentryBaseUrl}")

      // Build API URL
      val url = buildApiUrl(currentOptions, appId, version, binaryIdentifier)
      println("DistributionInternal: Constructed API URL: $url")

      // Make API request
      val response = currentHttpClient.get(url)

      if (!response.isSuccess) {
        println("DistributionInternal: API request failed with status ${response.statusCode}")
        return UpdateStatus.Error("API request failed: ${response.statusCode}")
      }

      // Parse response
      val parsedResponse = parseCheckForUpdatesResponse(response.body)
      if (parsedResponse == null) {
        println("DistributionInternal: Failed to parse API response")
        return UpdateStatus.Error("Failed to parse API response")
      }

      println("DistributionInternal: Successfully parsed API response")
      println(
        "  Current build: ${parsedResponse.current?.let { "${it.buildVersion} (${it.buildNumber})" } ?: "null"}"
      )
      println(
        "  Update available: ${parsedResponse.update?.let { "${it.buildVersion} (${it.buildNumber})" } ?: "null"}"
      )

      // Check if there's an update
      return if (parsedResponse.update != null) {
        UpdateStatus.NewRelease(parsedResponse.update.toUpdateInfo())
      } else {
        UpdateStatus.UpToDate
      }
    } catch (e: Exception) {
      println("DistributionInternal: Exception during update check: ${e.message}")
      e.printStackTrace()
      return UpdateStatus.Error("Error checking for updates: ${e.message}")
    }
  }

  /** Build the API URL for the check-for-updates endpoint. */
  private fun buildApiUrl(
    options: DistributionOptions,
    appId: String,
    version: String,
    binaryIdentifier: String,
  ): String {
    val baseUrl = options.sentryBaseUrl.removeSuffix("/")
    val orgSlug = URLEncoder.encode(options.organizationSlug, "UTF-8")
    val projectSlug = URLEncoder.encode(options.projectSlug, "UTF-8")

    println("DistributionInternal: Building API URL:")
    println("  Base URL: $baseUrl")
    println("  Org slug (raw): ${options.organizationSlug}")
    println("  Org slug (encoded): $orgSlug")
    println("  Project slug (raw): ${options.projectSlug}")
    println("  Project slug (encoded): $projectSlug")

    val url =
      StringBuilder(
        "$baseUrl/api/0/projects/$orgSlug/$projectSlug/preprodartifacts/check-for-updates/"
      )

    // Add query parameters
    val params = mutableListOf<String>()
    params.add("main_binary_identifier=${URLEncoder.encode(binaryIdentifier, "UTF-8")}")
    params.add("app_id=${URLEncoder.encode(appId, "UTF-8")}")
    params.add("platform=android")
    params.add("version=${URLEncoder.encode(version, "UTF-8")}")

    if (options.buildConfiguration != null) {
      params.add("build_configuration=${URLEncoder.encode(options.buildConfiguration, "UTF-8")}")
    }

    println("  Query parameters:")
    params.forEach { param -> println("    $param") }

    if (params.isNotEmpty()) {
      url.append("?").append(params.joinToString("&"))
    }

    val finalUrl = url.toString()
    println("  Final URL: $finalUrl")
    return finalUrl
  }
}
