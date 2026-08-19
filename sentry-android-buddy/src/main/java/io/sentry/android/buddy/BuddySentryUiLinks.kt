package io.sentry.android.buddy

import io.sentry.android.buddy.model.BuddySentryUiLinks

internal fun SentryBuddyOptions.sentryUiLinks(): BuddySentryUiLinks =
  BuddySentryUiLinks(
    baseUrl = sentryUiBaseUrl,
    organizationSlug = sentryUiOrganizationSlug,
    projectId = sentryUiProjectId,
  )
