# Build Distribution Feature Implementation Plan

Based on analysis of the Emerge Android distribution reference implementation and Sentry's API endpoint, here's a comprehensive plan for implementing the build distribution feature in the `sentry-android-distribution` module.

## 1. Core Architecture

### Public API (similar to Emerge pattern)
```kotlin
object Distribution {
    fun init(context: Context, options: DistributionOptions? = null)
    fun isEnabled(): Boolean
    suspend fun checkForUpdate(context: Context): UpdateStatus
    fun checkForUpdateCompletableFuture(context: Context): CompletableFuture<UpdateStatus>
    fun downloadUpdate(context: Context, info: UpdateInfo)
}
```

### Data Models
```kotlin
data class DistributionOptions(
    val orgAuthToken: String,
    val organizationSlug: String,
    val projectSlug: String,
    val buildConfiguration: String? = null
)

sealed class UpdateStatus {
    object UpToDate : UpdateStatus()
    class NewRelease(val info: UpdateInfo) : UpdateStatus()
    class Error(val message: String) : UpdateStatus()
}

data class UpdateInfo(
    val id: String,
    val buildVersion: String,
    val buildNumber: Int,
    val downloadUrl: String,
    val appName: String,
    val createdDate: String
)
```

## 2. Implementation Components

### A. ContentProvider for Auto-initialization
```kotlin
class DistributionContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { Distribution.init(it) }
        return true
    }
    // Minimal implementation for other required methods
}
```

### B. HTTP Client using Sentry's networking stack
- Utilize `HttpConnection` and `HttpUtils` from sentry module
- Create custom HTTP client for Sentry API calls
- Handle authentication with org auth token
- Implement proper error handling and timeouts

### C. Binary Identifier Detection
- Implement similar to Emerge's `BinaryIdentifier.kt`
- Extract app metadata (version name, version code, app ID)
- Generate unique identifier for current build

### D. API Integration
**Endpoint**: `/api/0/projects/{organization_slug}/{project_slug}/preprodartifacts/check-for-updates/`

**Request Parameters**:
- `main_binary_identifier` - Current app's binary identifier
- `app_id` - Application package name
- `platform` - "android"
- `version` - Current version name
- `build_configuration` - Optional build configuration name

**Response Handling**:
- Parse `CheckForUpdatesApiResponse` with `current` and `update` fields
- Map to internal `UpdateStatus` sealed class
- Handle error responses and network failures

### E. Background Processing
- Implement coroutine-based async processing
- Support both suspend functions and CompletableFuture for different use cases
- Handle cancellation and timeouts properly

## 3. Key Differences from Emerge Implementation

### Networking Stack
- **Emerge**: Uses OkHttp3 directly
- **Sentry**: Use existing `HttpConnection` and related utilities from sentry module
- Custom HTTP client wrapper for Sentry API authentication

### Auto-initialization
- **Emerge**: Uses androidx.startup with WorkManager dependency
- **Sentry**: Custom ContentProvider for auto-start (no external dependencies)

### API Endpoint
- **Emerge**: `https://api.emergetools.com/distribution/checkForUpdates`  
- **Sentry**: `{sentry_base_url}/api/0/organizations/{org}/projects/{project}/preprodartifacts/check-for-updates/`

### Authentication
- **Emerge**: API key in manifest metadata
- **Sentry**: Org auth token passed via DistributionOptions (hardcoded initially)

## 4. Implementation Files Structure

```
sentry-android-distribution/
├── src/main/java/io/sentry/android/distribution/
│   ├── Distribution.kt (Public API)
│   ├── DistributionContentProvider.kt (Auto-initialization)
│   ├── internal/
│   │   ├── DistributionInternal.kt (Core implementation)
│   │   ├── BinaryIdentifier.kt (App metadata detection)
│   │   ├── SentryHttpClient.kt (HTTP client wrapper)
│   │   └── Constants.kt (Constants)
│   └── models/
│       ├── DistributionOptions.kt
│       ├── UpdateStatus.kt
│       └── UpdateInfo.kt
└── src/main/AndroidManifest.xml (ContentProvider registration)
```

## 5. Integration Points

### Manifest Registration
```xml
<provider
    android:name="io.sentry.android.distribution.DistributionContentProvider"
    android:authorities="${applicationId}.sentry.distribution.provider"
    android:exported="false" />
```

### Dependencies
- **ZERO external dependencies** beyond the `sentry` module
- Use existing `sentry` module for networking utilities only
- No coroutines, no external serialization libraries
- Use Java's built-in concurrency (Thread, CompletableFuture)
- Use Android's built-in JSON parsing (org.json)
- No third-party HTTP libraries

## 6. Error Handling Strategy

- Network failures → return `UpdateStatus.Error`
- API errors → parse error messages from Sentry API
- Missing configuration → graceful degradation with logging
- Authentication failures → specific error messages
- Malformed responses → fallback error handling

## 7. Security Considerations

- Org auth token handling (initially hardcoded, future secure storage)
- HTTPS enforcement for all API calls
- Input validation for all API parameters
- Rate limiting awareness (API has 100 req/min limit)

## 8. API Details from Sentry Endpoint Analysis

### Request Format
```
GET /api/0/organizations/{organization_slug}/projects/{project_slug}/preprodartifacts/check-for-updates/
```

### Query Parameters
- `main_binary_identifier` (required) - Unique identifier for current binary
- `app_id` (required) - Application package name
- `platform` (required) - "android"
- `version` (required) - Current version name
- `build_configuration` (optional) - Build configuration name

### Response Format
```json
{
  "current": {
    "id": "artifact-id",
    "build_version": "1.0.0",
    "build_number": 100,
    "download_url": "https://...",
    "app_name": "MyApp",
    "created_date": "2025-01-01T00:00:00Z"
  },
  "update": {
    "id": "artifact-id",
    "build_version": "1.1.0", 
    "build_number": 101,
    "download_url": "https://...",
    "app_name": "MyApp",
    "created_date": "2025-01-02T00:00:00Z"
  }
}
```

### Authentication
- Uses org auth token in Authorization header
- Rate limited to 100 requests per minute per organization

This plan provides a solid foundation for implementing the build distribution feature while maintaining consistency with both the Emerge reference implementation and Sentry's existing patterns.
