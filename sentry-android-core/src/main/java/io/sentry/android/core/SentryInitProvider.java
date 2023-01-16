package io.sentry.android.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.ContentProviderSecurityChecker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public void attachInfo(@NotNull Context context, @NotNull ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryInitProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Override
  public @Nullable Cursor query(
      @NotNull Uri uri,
      @Nullable String[] strings,
      @Nullable String s,
      @Nullable String[] strings1,
      @Nullable String s1) {
    new ContentProviderSecurityChecker().checkPrivilegeEscalation(this);
    return null;
  }

  @Override
  public @Nullable String getType(@NotNull Uri uri) {
    return null;
  }

  @Override
  public @Nullable Uri insert(@NotNull Uri uri, @Nullable ContentValues contentValues) {
    return null;
  }

  @Override
  public int delete(@NotNull Uri uri, @Nullable String s, @Nullable String[] strings) {
    return 0;
  }

  @Override
  public int update(
      @NotNull Uri uri,
      @Nullable ContentValues contentValues,
      @Nullable String s,
      @Nullable String[] strings) {
    return 0;
  }
}
