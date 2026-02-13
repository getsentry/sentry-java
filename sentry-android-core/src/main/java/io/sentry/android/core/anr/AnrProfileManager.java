package io.sentry.android.core.anr;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.android.core.anr.AnrProfilingIntegration.POLLING_INTERVAL_MS;
import static io.sentry.android.core.anr.AnrProfilingIntegration.THRESHOLD_ANR_MS;

import io.sentry.ILogger;
import io.sentry.SentryOptions;
import io.sentry.cache.tape.ObjectQueue;
import io.sentry.cache.tape.QueueFile;
import io.sentry.util.Objects;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class AnrProfileManager implements AutoCloseable {

  private static final int MAX_NUM_STACKTRACES =
      (int) ((THRESHOLD_ANR_MS / POLLING_INTERVAL_MS) * 2);

  @NotNull private final ObjectQueue<AnrStackTrace> queue;

  public AnrProfileManager(final @NotNull SentryOptions options) {
    this(
        options,
        new File(
            Objects.requireNonNull(options.getCacheDirPath(), "cacheDirPath is required"),
            "anr_profile"));
  }

  public AnrProfileManager(final @NotNull SentryOptions options, final @NotNull File file) {
    final @NotNull ILogger logger = options.getLogger();

    @Nullable QueueFile queueFile = null;
    try {
      try {
        queueFile = new QueueFile.Builder(file).size(MAX_NUM_STACKTRACES).build();
      } catch (IOException e) {
        // if file is corrupted we simply delete it and try to create it again
        if (!file.delete()) {
          throw new IOException("Could not delete file");
        }
        queueFile = new QueueFile.Builder(file).size(MAX_NUM_STACKTRACES).build();
      }
    } catch (IOException e) {
      logger.log(ERROR, "Failed to create stacktrace queue", e);
    }

    if (queueFile == null) {
      queue = ObjectQueue.createEmpty();
    } else {
      queue =
          ObjectQueue.create(
              queueFile,
              new ObjectQueue.Converter<AnrStackTrace>() {
                @Override
                public AnrStackTrace from(final byte[] source) throws IOException {
                  // no need to close the streams since they are backed by byte arrays and don't
                  // hold any resources
                  final @NotNull ByteArrayInputStream bis = new ByteArrayInputStream(source);
                  final @NotNull DataInputStream dis = new DataInputStream(bis);
                  return AnrStackTrace.deserialize(dis);
                }

                @Override
                public void toStream(
                    final @NotNull AnrStackTrace value, final @NotNull OutputStream sink)
                    throws IOException {
                  try (final @NotNull DataOutputStream dos = new DataOutputStream(sink)) {
                    value.serialize(dos);
                    dos.flush();
                  }
                  sink.flush();
                }
              });
    }
  }

  public void clear() throws IOException {
    queue.clear();
  }

  public void add(AnrStackTrace trace) throws IOException {
    queue.add(trace);
  }

  @NotNull
  public AnrProfile load() throws IOException {
    return new AnrProfile(queue.asList());
  }

  @Override
  public void close() throws IOException {
    queue.close();
  }
}
