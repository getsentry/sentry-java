package io.sentry.instrumentation.file;

import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryOptions;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of {@link java.io.FileOutputStream} that creates a {@link io.sentry.ISpan} for
 * writing operation with filename and byte count set as description
 *
 * <p>Note, that span is started when this OutputStream is instantiated via constructor and finishes
 * when the {@link java.io.FileOutputStream#close()} is called.
 */
public final class SentryFileOutputStream extends FileOutputStream {

  private final @NotNull FileOutputStream delegate;
  private final @NotNull FileIOSpanManager spanManager;

  public SentryFileOutputStream(final @Nullable String name) throws FileNotFoundException {
    this(name != null ? new File(name) : null, false, HubAdapter.getInstance());
  }

  public SentryFileOutputStream(final @Nullable String name, final boolean append)
      throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, append, null, HubAdapter.getInstance()));
  }

  public SentryFileOutputStream(final @Nullable File file) throws FileNotFoundException {
    this(file, false, HubAdapter.getInstance());
  }

  public SentryFileOutputStream(final @Nullable File file, final boolean append)
      throws FileNotFoundException {
    this(init(file, append, null, HubAdapter.getInstance()));
  }

  public SentryFileOutputStream(final @NotNull FileDescriptor fdObj) {
    this(init(fdObj, null, HubAdapter.getInstance()), fdObj);
  }

  SentryFileOutputStream(final @Nullable File file, final boolean append, final @NotNull IHub hub)
      throws FileNotFoundException {
    this(init(file, append, null, hub));
  }

  private SentryFileOutputStream(
      final @NotNull FileOutputStreamInitData data, final @NotNull FileDescriptor fd) {
    super(fd);
    spanManager = new FileIOSpanManager(data.span, data.file, data.options);
    delegate = data.delegate;
  }

  private SentryFileOutputStream(final @NotNull FileOutputStreamInitData data)
      throws FileNotFoundException {
    super(getFileDescriptor(data.delegate));
    spanManager = new FileIOSpanManager(data.span, data.file, data.options);
    delegate = data.delegate;
  }

  private static FileOutputStreamInitData init(
      final @Nullable File file,
      final boolean append,
      @Nullable FileOutputStream delegate,
      @NotNull IHub hub)
      throws FileNotFoundException {
    final ISpan span = FileIOSpanManager.startSpan(hub, "file.write");
    if (delegate == null) {
      delegate = new FileOutputStream(file, append);
    }
    return new FileOutputStreamInitData(file, append, span, delegate, hub.getOptions());
  }

  private static FileOutputStreamInitData init(
      final @NotNull FileDescriptor fd, @Nullable FileOutputStream delegate, @NotNull IHub hub) {
    final ISpan span = FileIOSpanManager.startSpan(hub, "file.write");
    if (delegate == null) {
      delegate = new FileOutputStream(fd);
    }
    return new FileOutputStreamInitData(null, false, span, delegate, hub.getOptions());
  }

  @Override
  public void write(final int b) throws IOException {
    spanManager.performIO(
        () -> {
          delegate.write(b);
          return 1;
        });
  }

  @Override
  public void write(final byte @NotNull [] b) throws IOException {
    spanManager.performIO(
        () -> {
          delegate.write(b);
          return b.length;
        });
  }

  @Override
  public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
    spanManager.performIO(
        () -> {
          delegate.write(b, off, len);
          return len;
        });
  }

  @Override
  public void close() throws IOException {
    spanManager.finish(delegate);
  }

  private static FileDescriptor getFileDescriptor(final @NotNull FileOutputStream stream)
      throws FileNotFoundException {
    try {
      return stream.getFD();
    } catch (IOException error) {
      throw new FileNotFoundException("No file descriptor");
    }
  }

  public static final class Factory {
    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @Nullable String name)
        throws FileNotFoundException {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileOutputStream(
              init(name != null ? new File(name) : null, false, delegate, hub))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @Nullable String name, final boolean append)
        throws FileNotFoundException {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileOutputStream(
              init(name != null ? new File(name) : null, append, delegate, hub))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @Nullable File file)
        throws FileNotFoundException {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileOutputStream(init(file, false, delegate, hub))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @Nullable File file, final boolean append)
        throws FileNotFoundException {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileOutputStream(init(file, append, delegate, hub))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @NotNull FileDescriptor fdObj) {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileOutputStream(init(fdObj, delegate, hub), fdObj)
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate,
        final @Nullable File file,
        final @NotNull IHub hub)
        throws FileNotFoundException {
      return isTracingEnabled(hub)
          ? new SentryFileOutputStream(init(file, false, delegate, hub))
          : delegate;
    }

    private static boolean isTracingEnabled(final @NotNull IHub hub) {
      final @NotNull SentryOptions options = hub.getOptions();
      return options.isTracingEnabled();
    }
  }
}
