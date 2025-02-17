package io.sentry.cache;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;

import io.sentry.JsonDeserializer;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CacheUtils {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  static <T> void store(
      final @NotNull SentryOptions options,
      final @NotNull T entity,
      final @NotNull String dirName,
      final @NotNull String fileName) {
    final File cacheDir = ensureCacheDir(options, dirName);
    if (cacheDir == null) {
      options.getLogger().log(INFO, "Cache dir is not set, cannot store in scope cache");
      return;
    }

    final File file = new File(cacheDir, fileName);
    try (final OutputStream outputStream = new FileOutputStream(file);
        final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      options.getSerializer().serialize(entity, writer);
    } catch (Throwable e) {
      options.getLogger().log(ERROR, e, "Error persisting entity: %s", fileName);
    }
  }

  static void delete(
      final @NotNull SentryOptions options,
      final @NotNull String dirName,
      final @NotNull String fileName) {
    final File cacheDir = ensureCacheDir(options, dirName);
    if (cacheDir == null) {
      options.getLogger().log(INFO, "Cache dir is not set, cannot delete from scope cache");
      return;
    }

    final File file = new File(cacheDir, fileName);
    if (file.exists()) {
      options.getLogger().log(DEBUG, "Deleting %s from scope cache", fileName);
      if (!file.delete()) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to delete: %s", file.getAbsolutePath());
      }
    }
  }

  static <T, R> @Nullable T read(
      final @NotNull SentryOptions options,
      final @NotNull String dirName,
      final @NotNull String fileName,
      final @NotNull Class<T> clazz,
      final @Nullable JsonDeserializer<R> elementDeserializer) {
    final File cacheDir = ensureCacheDir(options, dirName);
    if (cacheDir == null) {
      options.getLogger().log(INFO, "Cache dir is not set, cannot read from scope cache");
      return null;
    }

    final File file = new File(cacheDir, fileName);
    if (file.exists()) {
      try (final Reader reader =
          new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))) {
        if (elementDeserializer == null) {
          return options.getSerializer().deserialize(reader, clazz);
        } else {
          return options.getSerializer().deserializeCollection(reader, clazz, elementDeserializer);
        }
      } catch (Throwable e) {
        options.getLogger().log(ERROR, e, "Error reading entity from scope cache: %s", fileName);
      }
    } else {
      options.getLogger().log(DEBUG, "No entry stored for %s", fileName);
    }
    return null;
  }

  static @Nullable File ensureCacheDir(
      final @NotNull SentryOptions options, final @NotNull String cacheDirName) {
    final String cacheDir = options.getCacheDirPath();
    if (cacheDir == null) {
      return null;
    }
    final File dir = new File(cacheDir, cacheDirName);
    dir.mkdirs();
    return dir;
  }
}
