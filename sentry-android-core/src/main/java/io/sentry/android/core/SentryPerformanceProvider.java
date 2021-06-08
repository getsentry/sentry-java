package io.sentry.android.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.DateUtils;
import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * SentryPerformanceProvider is responsible for collecting data (eg appStart) as early as possible
 * as ContentProvider is the only reliable hook for libraries that works across all the supported
 * SDK versions. When minSDK is >= 24, we could use Process.getStartUptimeMillis()
 */
@ApiStatus.Internal
public final class SentryPerformanceProvider extends ContentProvider {

  // static to rely on Class load
  private static final @NotNull Date appStartTime = DateUtils.getCurrentDateTime();
  // SystemClock.uptimeMillis() isn't affected by phone provider or clock changes.
  private static final long appStartMillis = SystemClock.uptimeMillis();

  public SentryPerformanceProvider() {
    AppStartState.getInstance().setAppStartTime(appStartMillis, appStartTime);
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public void attachInfo(Context context, ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (SentryPerformanceProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Nullable
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] projection,
      @Nullable String selection,
      @Nullable String[] selectionArgs,
      @Nullable String sortOrder) {
    return null;
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    return null;
  }

  @Override
  public int delete(
      @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues values,
      @Nullable String selection,
      @Nullable String[] selectionArgs) {
    return 0;
  }
}
