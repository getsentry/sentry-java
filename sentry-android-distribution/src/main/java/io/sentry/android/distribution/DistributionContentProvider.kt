package io.sentry.android.distribution

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * ContentProvider that automatically initializes the Sentry Distribution SDK.
 *
 * This provider is automatically instantiated by the Android system when the app starts, ensuring
 * the Distribution SDK is available without requiring manual initialization in
 * Application.onCreate().
 */
public class DistributionContentProvider : ContentProvider() {
  override fun onCreate(): Boolean {
    // TODO: Automatic initialization will be implemented in future PR
    return true
  }

  // Required ContentProvider methods (not used for initialization)
  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?,
  ): Cursor? = null

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<String>?,
  ): Int = 0
}
