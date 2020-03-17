package io.sentry.core.cache;

import static io.sentry.core.SentryLevel.DEBUG;
import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.SentryLevel.WARNING;
import static java.lang.String.format;

import io.sentry.core.ISerializer;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.util.Objects;
import java.io.BufferedInputStream;
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
import java.nio.charset.Charset;
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
public final class DiskCache implements IEventCache {
  /** File suffix added to all serialized event files. */
  public static final String FILE_SUFFIX = ".sentry-event";

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final File directory;
  private final int maxSize;
  private final ISerializer serializer;
  private final SentryOptions options;

  public DiskCache(SentryOptions options) {
    Objects.requireNonNull(options.getCacheDirPath(), "Cache dir. path is required.");
    this.directory = new File(options.getCacheDirPath());
    this.maxSize = options.getCacheDirSize();
    this.serializer = options.getSerializer();
    this.options = options;
  }

  @Override
  public void store(SentryEvent event) {
    if (getNumberOfStoredEvents() >= maxSize) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disk cache full (respecting maxSize). Not storing event {}",
              event);
      return;
    }

    File eventFile = getEventFile(event);
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

    try (OutputStream fileOutputStream = new FileOutputStream(eventFile);
        Writer wrt = new OutputStreamWriter(fileOutputStream, UTF_8)) {
      serializer.serialize(event, wrt);
    } catch (Exception e) {
      options
          .getLogger()
          .log(ERROR, "Error writing Event to offline storage: %s", event.getEventId());
    }
  }

  @Override
  public void discard(SentryEvent event) {
    File eventFile = getEventFile(event);
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

  private int getNumberOfStoredEvents() {
    return allEventFiles().length;
  }

  private boolean isDirectoryValid() {
    if (!directory.isDirectory() || !directory.canWrite() || !directory.canRead()) {
      options
          .getLogger()
          .log(
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
      try (Reader rdr =
          new InputStreamReader(new BufferedInputStream(new FileInputStream(f)), UTF_8)) {

        ret.add(serializer.deserializeEvent(rdr));
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

  private File[] allEventFiles() {
    if (isDirectoryValid()) {
      // TODO: we need to order by oldest to the newest here
      return directory.listFiles((__, fileName) -> fileName.endsWith(FILE_SUFFIX));
    }
    return new File[] {};
  }
}
