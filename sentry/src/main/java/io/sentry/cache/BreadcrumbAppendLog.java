package io.sentry.cache;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;

import io.sentry.Breadcrumb;
import io.sentry.SentryOptions;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A bounded, newline-delimited JSON append log for breadcrumbs.
 *
 * <p>Breadcrumbs are only ever appended in bulk and read back as a whole list on the next launch,
 * so this stores one serialized breadcrumb per line rather than using a random-access queue. A torn
 * trailing line — the realistic failure when the process dies mid-write — fails to deserialize and
 * is skipped, leaving every earlier breadcrumb readable.
 *
 * <p>The log is bounded by rewriting it down to the newest {@code maxBreadcrumbs} entries once it
 * grows past {@code compactionThresholdFactor} times that limit, so appends stay O(1) amortized.
 *
 * <p>Not thread-safe; callers must serialize access. {@link PersistingScopeObserver} only touches
 * it from the flush task on its single-threaded executor.
 *
 * <p>A null file means there is nowhere to persist to (no cache dir configured); every operation
 * then becomes a no-op.
 */
@ApiStatus.Internal
public final class BreadcrumbAppendLog {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /** Compact once the log holds this many times {@code maxBreadcrumbs} lines. */
  private static final int COMPACTION_THRESHOLD_FACTOR = 2;

  private final @NotNull SentryOptions options;
  private final @Nullable File file;
  private final int maxBreadcrumbs;

  /**
   * Lines currently in the file. Tracked in memory so appends don't have to count them on disk;
   * seeded from the file on first use so a log inherited from the previous run is still bounded.
   */
  private int lineCount;

  private boolean lineCountKnown;

  public BreadcrumbAppendLog(final @NotNull SentryOptions options, final @Nullable File file) {
    this.options = options;
    this.file = file;
    // a non-positive limit would make the compaction threshold zero and compact on every append
    this.maxBreadcrumbs = Math.max(1, options.getMaxBreadcrumbs());
  }

  /** Appends breadcrumbs, compacting afterwards if the log has outgrown its bound. */
  public void append(final @NotNull List<Breadcrumb> breadcrumbs) {
    if (file == null || breadcrumbs.isEmpty()) {
      return;
    }
    // otherwise we'd append newline-delimited JSON onto a file in some older format
    discardForeignFormat();
    ensureLineCount();

    int appended = 0;
    try (final Writer writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), UTF_8))) {
      for (final @NotNull Breadcrumb crumb : breadcrumbs) {
        try {
          options.getSerializer().serialize(crumb, writer);
        } catch (Throwable e) {
          // the partially written line would corrupt the log, so stop rather than write more
          options.getLogger().log(ERROR, e, "Error serializing breadcrumb, dropping the rest");
          break;
        }
        writer.write('\n');
        appended++;
      }
    } catch (Throwable e) {
      // the line count is unreliable once a write fails part-way, so re-read it next time
      options.getLogger().log(ERROR, e, "Error appending breadcrumbs to %s", file.getName());
      lineCountKnown = false;
      return;
    }

    lineCount += appended;
    if (lineCount > maxBreadcrumbs * COMPACTION_THRESHOLD_FACTOR) {
      compact();
    }
  }

  /** Reads the persisted breadcrumbs, oldest first, skipping any line that fails to parse. */
  public @NotNull List<Breadcrumb> read() {
    final @NotNull List<Breadcrumb> breadcrumbs = readAll();
    return breadcrumbs.size() <= maxBreadcrumbs
        ? breadcrumbs
        : new ArrayList<>(
            breadcrumbs.subList(breadcrumbs.size() - maxBreadcrumbs, breadcrumbs.size()));
  }

  /** Empties the log. */
  public void clear() {
    if (file == null) {
      return;
    }
    if (file.exists() && !file.delete()) {
      options.getLogger().log(DEBUG, "Failed to delete %s", file.getAbsolutePath());
    }
    lineCount = 0;
    lineCountKnown = true;
  }

  private @NotNull List<Breadcrumb> readAll() {
    discardForeignFormat();
    if (file == null || !file.exists()) {
      return new ArrayList<>();
    }

    final @NotNull List<Breadcrumb> breadcrumbs = new ArrayList<>();
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          continue;
        }
        final @Nullable Breadcrumb crumb = deserialize(line);
        if (crumb != null) {
          breadcrumbs.add(crumb);
        }
      }
    } catch (Throwable e) {
      options.getLogger().log(ERROR, e, "Error reading breadcrumbs from %s", file.getName());
    }
    return breadcrumbs;
  }

  /**
   * Deletes a file left behind by an older SDK version, which stored breadcrumbs either as a
   * QueueFile ring buffer or as a single JSON array. Neither is newline-delimited, so reading them
   * line-by-line yields nothing useful; delete instead of leaving stale bytes to be appended to.
   */
  private void discardForeignFormat() {
    if (file == null || !file.exists()) {
      return;
    }
    final int firstByte = readFirstByte();
    // every line this class writes is a JSON object, so anything else is a foreign format
    if (firstByte == -1 || firstByte == '{') {
      return;
    }
    options.getLogger().log(DEBUG, "Discarding breadcrumbs in an unrecognized format");
    clear();
  }

  private int readFirstByte() {
    if (file == null) {
      return -1;
    }
    try (final FileInputStream stream = new FileInputStream(file)) {
      return stream.read();
    } catch (Throwable e) {
      options.getLogger().log(ERROR, e, "Error reading breadcrumbs from %s", file.getName());
      return -1;
    }
  }

  private @Nullable Breadcrumb deserialize(final @NotNull String line) {
    try (final StringReader reader = new StringReader(line)) {
      return options.getSerializer().deserialize(reader, Breadcrumb.class);
    } catch (Throwable e) {
      // a torn trailing line is expected after the process died mid-write, so this isn't an error
      options.getLogger().log(DEBUG, "Skipping unreadable breadcrumb line");
      return null;
    }
  }

  /** Rewrites the log with only the newest {@code maxBreadcrumbs} entries. */
  private void compact() {
    if (file == null) {
      return;
    }
    final @NotNull List<Breadcrumb> retained = read();
    final @NotNull File tempFile = new File(file.getPath() + ".tmp");

    int written = 0;
    try (final Writer writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile), UTF_8))) {
      for (final @NotNull Breadcrumb crumb : retained) {
        options.getSerializer().serialize(crumb, writer);
        writer.write('\n');
        written++;
      }
    } catch (Throwable e) {
      options.getLogger().log(ERROR, e, "Error compacting breadcrumbs, keeping the existing log");
      if (tempFile.exists() && !tempFile.delete()) {
        options.getLogger().log(DEBUG, "Failed to delete %s", tempFile.getAbsolutePath());
      }
      return;
    }

    // renameTo is atomic, so a crash here leaves either the old log or the compacted one
    if (!tempFile.renameTo(file)) {
      options.getLogger().log(ERROR, "Failed to replace %s with the compacted log", file.getName());
      if (!tempFile.delete()) {
        options.getLogger().log(DEBUG, "Failed to delete %s", tempFile.getAbsolutePath());
      }
      return;
    }
    lineCount = written;
    lineCountKnown = true;
  }

  private void ensureLineCount() {
    if (lineCountKnown) {
      return;
    }
    lineCount = countLines();
    lineCountKnown = true;
  }

  private int countLines() {
    if (file == null || !file.exists()) {
      return 0;
    }
    int lines = 0;
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))) {
      while (reader.readLine() != null) {
        lines++;
      }
    } catch (Throwable e) {
      options.getLogger().log(ERROR, e, "Error counting breadcrumbs in %s", file.getName());
    }
    return lines;
  }
}
