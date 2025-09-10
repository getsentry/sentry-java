package io.sentry.android.distribution

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * ContentProvider that automatically initializes the Distribution SDK when the app starts.
 *
 * This ContentProvider is registered in the AndroidManifest.xml and will be instantiated during app
 * startup, providing an opportunity to initialize the Distribution SDK without requiring explicit
 * initialization in Application.onCreate().
 *
 * Note: This only handles auto-initialization when Distribution options are configured via manifest
 * metadata or other automatic configuration methods. Manual initialization via Distribution.init()
 * is still required when using programmatic configuration.
 */
public class DistributionContentProvider : ContentProvider() {

  override fun onCreate(): Boolean {
    // TODO: Implement automatic initialization based on manifest metadata
    // For now, this ContentProvider is just a placeholder for future auto-init functionality
    // Manual initialization via Distribution.init() is still required
    return true
  }

  // Minimal implementations for required ContentProvider methods
  // These are not used for our auto-initialization purpose

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? = null

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0
}
