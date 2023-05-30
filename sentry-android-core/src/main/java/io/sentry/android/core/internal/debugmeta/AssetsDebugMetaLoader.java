package io.sentry.android.core.internal.debugmeta;

import static io.sentry.util.DebugMetaPropertiesApplier.DEBUG_META_PROPERTIES_FILENAME;

import android.content.Context;
import android.content.res.AssetManager;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.internal.debugmeta.IDebugMetaLoader;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AssetsDebugMetaLoader implements IDebugMetaLoader {
  private final @NotNull Context context;
  private final @NotNull ILogger logger;

  public AssetsDebugMetaLoader(final @NotNull Context context, final @NotNull ILogger logger) {
    this.context = context;
    this.logger = logger;
  }

  @Override
  public @Nullable Properties loadDebugMeta() {
    final AssetManager assets = context.getAssets();
    // one may have thousands of asset files and looking up this list might slow down the SDK init.
    // quite a bit, for this reason, we try to open the file directly and take care of errors
    // like FileNotFoundException
    try (final InputStream is =
        new BufferedInputStream(assets.open(DEBUG_META_PROPERTIES_FILENAME))) {
      final Properties properties = new Properties();
      properties.load(is);
      return properties;
    } catch (FileNotFoundException e) {
      logger.log(SentryLevel.INFO, e, "%s file was not found.", DEBUG_META_PROPERTIES_FILENAME);
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error getting Proguard UUIDs.", e);
    } catch (RuntimeException e) {
      logger.log(SentryLevel.ERROR, e, "%s file is malformed.", DEBUG_META_PROPERTIES_FILENAME);
    }

    return null;
  }
}
