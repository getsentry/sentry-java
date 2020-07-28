package io.sentry.core.cache;

import static io.sentry.core.SentryLevel.DEBUG;
import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.SentryLevel.WARNING;
import static java.lang.String.format;

import io.sentry.core.SentryEvent;
import io.sentry.core.SentryOptions;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A simple cache implementation storing the events to a disk, each event in a separater file in a
 * configured directory.
 */
@ApiStatus.Internal
public final class DiskCache extends CacheStrategy implements IEventCache {
  /** File suffix added to all serialized event files. */
  public static final String FILE_SUFFIX = ".sentry-event";

  public DiskCache(final @NotNull SentryOptions options) {
    super(options, options.getCacheDirPath(), options.getCacheDirSize());
  }

  @Override
  public void store(final @NotNull SentryEvent event) {
    rotateCacheIfNeeded(allEventFiles());

    final File eventFile = getEventFile(event);
    if (eventFile.exists()) {
      options
          .getLogger()
          .log(
              WARNING,
              "Not adding Event to offline storage because it already exists: %s",
              eventFile.getAbsolutePath());
      return;
    } else {
      options
          .getLogger()
          .log(DEBUG, "Adding Event to offline storage: %s", eventFile.getAbsolutePath());
    }

    try (final OutputStream outputStream = new FileOutputStream(eventFile);
        final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      serializer.serialize(event, writer);
    } catch (Exception e) {
      options
          .getLogger()
          .log(ERROR, e, "Error writing Event to offline storage: %s", event.getEventId());
    }
  }

  @Override
  public void discard(final @NotNull SentryEvent event) {
    final File eventFile = getEventFile(event);
    if (eventFile.exists()) {
      options
          .getLogger()
          .log(DEBUG, "Discarding event from cache: %s", eventFile.getAbsolutePath());

      if (!eventFile.delete()) {
        options.getLogger().log(ERROR, "Failed to delete Event: %s", eventFile.getAbsolutePath());
      }
    } else {
      options.getLogger().log(DEBUG, "Event was not cached: %s", eventFile.getAbsolutePath());
    }
  }

  private @NotNull File getEventFile(final @NotNull SentryEvent event) {
    return new File(directory.getAbsolutePath(), event.getEventId().toString() + FILE_SUFFIX);
  }

  @Override
  public @NotNull Iterator<SentryEvent> iterator() {
    final File[] allCachedEvents = allEventFiles();

    final List<SentryEvent> ret = new ArrayList<>(allCachedEvents.length);

    for (final File f : allCachedEvents) {
      try (final Reader reader =
          new BufferedReader(new InputStreamReader(new FileInputStream(f), UTF_8))) {

        ret.add(serializer.deserializeEvent(reader));
      } catch (FileNotFoundException e) {
        options
            .getLogger()
            .log(
                DEBUG,
                "Event file '%s' disappeared while converting all cached files to events.",
                f.getAbsolutePath());
      } catch (IOException e) {
        options
            .getLogger()
            .log(
                ERROR,
                format("Error while reading cached event from file %s", f.getAbsolutePath()),
                e);
      }
    }

    return ret.iterator();
  }

  private @NotNull File[] allEventFiles() {
    if (isDirectoryValid()) {
      final File[] files = directory.listFiles((__, fileName) -> fileName.endsWith(FILE_SUFFIX));
      if (files != null) {
        return files;
      }
    }
    return new File[] {};
  }
}
