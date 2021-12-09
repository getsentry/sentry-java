package io.sentry.instrumentation.file;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.util.Pair;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of {@link java.io.FileInputStream} that creates a {@link io.sentry.ISpan} for
 * reading operation with filename and byte count set as description
 *
 * Note, that span is started when this InputStream is instantiated via constructor and finishes
 * when the {@link java.io.FileInputStream#close()} is called.
 */
@Open
public class SentryFileInputStream extends FileInputStream {

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

  public SentryFileInputStream(final @Nullable String name, final @NotNull IHub hub)
    throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, null, hub));
  }

  public SentryFileInputStream(final @Nullable File file, final @NotNull IHub hub)
    throws FileNotFoundException {
    this(init(file, null, hub));
  }

  public SentryFileInputStream(final @NotNull FileDescriptor fdObj, final @NotNull IHub hub) {
    this(init(fdObj, null, hub), fdObj);
  }

  private SentryFileInputStream(
    final @NotNull FileInputStreamInitData data,
    final @NotNull FileDescriptor fd
  ) {
    super(fd);
    spanManager = new FileIOSpanManager(data.span, data.file, data.hub);
    delegate = data.delegate;
  }

  private SentryFileInputStream(
    final @NotNull FileInputStreamInitData data
  ) throws FileNotFoundException {
    super(data.file);
    spanManager = new FileIOSpanManager(data.span, data.file, data.hub);
    delegate = data.delegate;
  }

  private static FileInputStreamInitData init(
    final @Nullable File file,
    @Nullable FileInputStream delegate,
    final @NotNull IHub hub
  ) throws FileNotFoundException {
    final ISpan span = FileIOSpanManager.startSpan(hub, "file.read");
    if (delegate == null) {
      delegate = new FileInputStream(file);
    }
    return new FileInputStreamInitData(file, span, delegate, hub);
  }

  private static FileInputStreamInitData init(
    final @NotNull FileDescriptor fd,
    @Nullable FileInputStream delegate,
    final @NotNull IHub hub
  ) {
    final ISpan span = FileIOSpanManager.startSpan(hub, "file.read");
    if (delegate == null) {
      delegate = new FileInputStream(fd);
    }
    return new FileInputStreamInitData(null, span, delegate, hub);
  }

  @Override
  public int read() throws IOException {
    return spanManager.performIO(() -> {
      int result = delegate.read();
      return new Pair<>(result, 1);
    });
  }

  @Override
  public int read(final byte @NotNull [] b) throws IOException {
    return spanManager.performIO(() -> {
      int result = delegate.read(b);
      return new Pair<>(result, b.length);
    });
  }

  @Override
  public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
    return spanManager.performIO(() -> {
      int result = delegate.read(b, off, len);
      return new Pair<>(result, len);
    });
  }

  @Override
  public long skip(final long n) throws IOException {
    return spanManager.performIO(() -> {
      long result = delegate.skip(n);
      return new Pair<>(result, result);
    });
  }

  @Override
  public void close() throws IOException {
    spanManager.finish(delegate);
  }

  public final static class Factory {
    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @Nullable String name
    ) throws FileNotFoundException {
      return new SentryFileInputStream(
        init(name != null ? new File(name) : null, delegate, HubAdapter.getInstance()));
    }

    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @Nullable File file
    ) throws FileNotFoundException {
      return new SentryFileInputStream(init(file, delegate, HubAdapter.getInstance()));
    }

    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @NotNull FileDescriptor descriptor
    ) {
      return new SentryFileInputStream(init(descriptor, delegate, HubAdapter.getInstance()),
        descriptor);
    }

    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @Nullable String name,
      final @NotNull IHub hub
    ) throws FileNotFoundException {
      return new SentryFileInputStream(
        init(name != null ? new File(name) : null, delegate, hub));
    }

    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @Nullable File file,
      final @NotNull IHub hub
    ) throws FileNotFoundException {
      return new SentryFileInputStream(init(file, delegate, hub));
    }

    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @NotNull FileDescriptor descriptor,
      final @NotNull IHub hub
    ) {
      return new SentryFileInputStream(init(descriptor, delegate, hub),
        descriptor);
    }
  }
}
