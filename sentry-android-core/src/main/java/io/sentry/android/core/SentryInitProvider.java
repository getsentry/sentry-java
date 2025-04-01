package io.sentry.android.core;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryInitProvider extends EmptySecureContentProvider {

  @Override
  public boolean onCreate() {
    AndroidLogger logger = new AndroidLogger();
    final Context context = getContext();
    if (context == null) {
      if (logger.isEnabled(SentryLevel.FATAL)) {
        logger.log(SentryLevel.FATAL, "App. Context from ContentProvider is null");
      }
      return false;
    }
    if (ManifestMetadataReader.isAutoInit(context, logger)) {
      SentryAndroid.init(context, logger);
      SentryIntegrationPackageStorage.getInstance().addIntegration("AutoInit");
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
  public @Nullable String getType(@NotNull Uri uri) {
    return null;
  }
}
