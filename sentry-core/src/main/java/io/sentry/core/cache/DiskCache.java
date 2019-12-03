package io.sentry.core.cache;

import static io.sentry.core.ILogger.logIfNotNull;
import static io.sentry.core.SentryLevel.DEBUG;
import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.SentryLevel.WARNING;
import static java.lang.String.format;

import io.sentry.core.ISerializer;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A simple cache implementation storing the events to a disk, each event in a separater file in a
 * configured directory.
 */
public final class DiskCache implements IEventCache {
  /** File suffix added to all serialized event files. */
  public static final String FILE_SUFFIX = ".sentry-event";

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF8 = Charset.forName("UTF-8");

  private final File directory;
  private final int maxSize;
  private final ISerializer serializer;
  private final SentryOptions options;

  public DiskCache(SentryOptions options) {
    this.directory = new File(options.getCacheDirPath());
    this.maxSize = options.getCacheDirSize();
    this.serializer = options.getSerializer();
    this.options = options;
  }

  @Override
  public void store(SentryEvent event) {
    if (getNumberOfStoredEvents() >= maxSize) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Disk cache full (respecting maxSize). Not storing event {}",
          event);
      return;
    }

    File eventFile = getEventFile(event);
    if (eventFile.exists()) {
      logIfNotNull(
          options.getLogger(),
          WARNING,
          "Not adding Event to offline storage because it already exists: %s",
          eventFile.getAbsolutePath());
      return;
    } else {
      logIfNotNull(
          options.getLogger(),
          DEBUG,
          "Adding Event to offline storage: %s",
          eventFile.getAbsolutePath());
    }

    try (FileOutputStream fileOutputStream = new FileOutputStream(eventFile);
        Writer wrt = new OutputStreamWriter(fileOutputStream, UTF8)) {
      serializer.serialize(event, wrt);
    } catch (Exception e) {
      logIfNotNull(
          options.getLogger(),
          ERROR,
          "Error writing Event to offline storage: %s",
          event.getEventId());
    }
  }

  @Override
  public void discard(SentryEvent event) {
    File eventFile = getEventFile(event);
    if (eventFile.exists()) {
      logIfNotNull(
          options.getLogger(),
          DEBUG,
          "Discarding event from cache: %s",
          eventFile.getAbsolutePath());

      if (!eventFile.delete()) {
        logIfNotNull(
            options.getLogger(), ERROR, "Failed to delete Event: %s", eventFile.getAbsolutePath());
      }
    } else {
      logIfNotNull(
          options.getLogger(), DEBUG, "Event was not cached: %s", eventFile.getAbsolutePath());
    }
  }

  private int getNumberOfStoredEvents() {
    return allEventFiles().length;
  }

  private boolean isDirectoryValid() {
    if (!directory.isDirectory() || !directory.canWrite() || !directory.canRead()) {
      logIfNotNull(
          options.getLogger(),
          ERROR,
          "The directory for caching Sentry events is inaccessible.: %s",
          directory.getAbsolutePath());
      return false;
    }
    return true;
  }

  private File getEventFile(SentryEvent event) {
    return new File(directory.getAbsolutePath(), event.getEventId().toString() + FILE_SUFFIX);
  }

  @Override
  public @NotNull Iterator<SentryEvent> iterator() {
    File[] allCachedEvents = allEventFiles();

    List<SentryEvent> ret = new ArrayList<>(allCachedEvents.length);

    for (File f : allCachedEvents) {
      try (InputStreamReader rdr =
          new InputStreamReader(new BufferedInputStream(new FileInputStream(f)), UTF8)) {

        ret.add(serializer.deserializeEvent(rdr));
      } catch (FileNotFoundException e) {
        logIfNotNull(
            options.getLogger(),
            DEBUG,
            "Event file '%s' disappeared while converting all cached files to events.",
            f.getAbsolutePath());
      } catch (IOException e) {
        logIfNotNull(
            options.getLogger(),
            ERROR,
            format("Error while reading cached event from file %s", f.getAbsolutePath()),
            e);
      }
    }

    return ret.iterator();
  }

  private File[] allEventFiles() {
    if (isDirectoryValid()) {
      // TODO: we need to order by oldest to the newest here
      return directory.listFiles((__, fileName) -> fileName.endsWith(FILE_SUFFIX));
    }
    return new File[] {};
  }
}
