package io.sentry.instrumentation.file;

import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryOptions;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of {@link java.io.FileInputStream} that creates a {@link io.sentry.ISpan} for
 * reading operation with filename and byte count set as description
 *
 * <p>Note, that span is started when this InputStream is instantiated via constructor and finishes
 * when the {@link java.io.FileInputStream#close()} is called.
 */
public final class SentryFileInputStream extends FileInputStream {

  private final @NotNull FileInputStream delegate;
  private final @NotNull FileIOSpanManager spanManager;

  public SentryFileInputStream(final @Nullable String name) throws FileNotFoundException {
    this(name != null ? new File(name) : null, HubAdapter.getInstance());
  }

  public SentryFileInputStream(final @Nullable File file) throws FileNotFoundException {
    this(file, HubAdapter.getInstance());
  }

  public SentryFileInputStream(final @NotNull FileDescriptor fdObj) {
    this(fdObj, HubAdapter.getInstance());
  }

  SentryFileInputStream(final @Nullable File file, final @NotNull IHub hub)
      throws FileNotFoundException {
    this(init(file, null, hub));
  }

  SentryFileInputStream(final @NotNull FileDescriptor fdObj, final @NotNull IHub hub) {
    this(init(fdObj, null, hub), fdObj);
  }

  private SentryFileInputStream(
      final @NotNull FileInputStreamInitData data, final @NotNull FileDescriptor fd) {
    super(fd);
    spanManager = new FileIOSpanManager(data.span, data.file, data.options);
    delegate = data.delegate;
  }

  private SentryFileInputStream(final @NotNull FileInputStreamInitData data)
      throws FileNotFoundException {
    super(getFileDescriptor(data.delegate));
    spanManager = new FileIOSpanManager(data.span, data.file, data.options);
    delegate = data.delegate;
  }

  private static FileInputStreamInitData init(
      final @Nullable File file, @Nullable FileInputStream delegate, final @NotNull IHub hub)
      throws FileNotFoundException {
    final ISpan span = FileIOSpanManager.startSpan(hub, "file.read");
    if (delegate == null) {
      delegate = new FileInputStream(file);
    }
    return new FileInputStreamInitData(file, span, delegate, hub.getOptions());
  }

  private static FileInputStreamInitData init(
      final @NotNull FileDescriptor fd,
      @Nullable FileInputStream delegate,
      final @NotNull IHub hub) {
    final ISpan span = FileIOSpanManager.startSpan(hub, "file.read");
    if (delegate == null) {
      delegate = new FileInputStream(fd);
    }
    return new FileInputStreamInitData(null, span, delegate, hub.getOptions());
  }

  @Override
  public int read() throws IOException {
    // this is the only case, when the read() operation returns the byte value, and not the count
    // hence we need this special handling
    AtomicInteger result = new AtomicInteger(0);
    spanManager.performIO(
        () -> {
          final int res = delegate.read();
          result.set(res);
          return res != -1 ? 1 : 0;
        });
    return result.get();
  }

  @Override
  public int read(final byte @NotNull [] b) throws IOException {
    return spanManager.performIO(() -> delegate.read(b));
  }

  @Override
  public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
    return spanManager.performIO(() -> delegate.read(b, off, len));
  }

  @Override
  public long skip(final long n) throws IOException {
    return spanManager.performIO(() -> delegate.skip(n));
  }

  @Override
  public void close() throws IOException {
    spanManager.finish(delegate);
  }

  private static FileDescriptor getFileDescriptor(final @NotNull FileInputStream stream)
      throws FileNotFoundException {
    try {
      return stream.getFD();
    } catch (IOException error) {
      throw new FileNotFoundException("No file descriptor");
    }
  }

  public static final class Factory {
    public static FileInputStream create(
        final @NotNull FileInputStream delegate, final @Nullable String name)
        throws FileNotFoundException {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileInputStream(init(name != null ? new File(name) : null, delegate, hub))
          : delegate;
    }

    public static FileInputStream create(
        final @NotNull FileInputStream delegate, final @Nullable File file)
        throws FileNotFoundException {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileInputStream(init(file, delegate, hub))
          : delegate;
    }

    public static FileInputStream create(
        final @NotNull FileInputStream delegate, final @NotNull FileDescriptor descriptor) {
      final @NotNull IHub hub = HubAdapter.getInstance();
      return isTracingEnabled(hub)
          ? new SentryFileInputStream(init(descriptor, delegate, hub), descriptor)
          : delegate;
    }

    static FileInputStream create(
        final @NotNull FileInputStream delegate, final @Nullable File file, final @NotNull IHub hub)
        throws FileNotFoundException {
      return isTracingEnabled(hub)
          ? new SentryFileInputStream(init(file, delegate, hub))
          : delegate;
    }

    private static boolean isTracingEnabled(final @NotNull IHub hub) {
      final @NotNull SentryOptions options = hub.getOptions();
      return options.isTracingEnabled();
    }
  }
}
