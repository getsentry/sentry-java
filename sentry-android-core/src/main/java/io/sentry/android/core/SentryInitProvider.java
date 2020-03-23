package io.sentry.android.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import io.sentry.core.Sentry;
import io.sentry.core.SentryLevel;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class SentryInitProvider extends ContentProvider {

  @Override
  public boolean onCreate() {
    AndroidLogger logger = new AndroidLogger();
    final Context context = getContext();
    if (context == null) {
      logger.log(SentryLevel.FATAL, "App. Context from ContentProvider is null");
      return false;
    }
    if (ManifestMetadataReader.isAutoInit(context, logger)) {
      SentryAndroid.init(context, logger);
    }
    return true;
  }

  @Override
  public void shutdown() {
    Sentry.close();
  }

  @Override
  public void attachInfo(Context context, ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryInitProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Override
  public Cursor query(Uri uri, String[] strings, String s, String[] strings1, String s1) {
    return null;
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues contentValues) {
    return null;
  }

  @Override
  public int delete(Uri uri, String s, String[] strings) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
    return 0;
  }
}
