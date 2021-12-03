package io.sentry.instrumentation.file;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
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
 * Note, that span is started when this InputStream is instantiated via constructor and finishes
 * when the {@link java.io.FileInputStream#close()} is called.
 */
@SuppressWarnings("unused") // used via bytecode manipulation (SAGP)
@Open
public class SentryFileInputStream extends FileInputStream {

  private final @NotNull FileInputStream delegate;
  private final @NotNull FileIOSpanManager spanManager;

  public SentryFileInputStream(final @Nullable String name) throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, null));
  }

  public SentryFileInputStream(final @Nullable File file) throws FileNotFoundException {
    this(init(file, null));
  }

  public SentryFileInputStream(final @NotNull FileDescriptor fdObj) {
    this(init(fdObj, null), fdObj);
  }

  private SentryFileInputStream(
    final @NotNull FileInputStreamInitData data,
    final @NotNull FileDescriptor fd
  ) {
    super(fd);
    spanManager = new FileIOSpanManager(data.span, data.file);
    delegate = data.delegate;
  }

  private SentryFileInputStream(
    final @NotNull FileInputStreamInitData data
  ) throws FileNotFoundException {
    super(data.file);
    spanManager = new FileIOSpanManager(data.span, data.file);
    delegate = data.delegate;
  }

  private static FileInputStreamInitData init(
    final @Nullable File file,
    @Nullable FileInputStream delegate
  ) throws FileNotFoundException {
    final ISpan span = FileIOSpanManager.startSpan("file.read");
    if (delegate == null) {
      delegate = new FileInputStream(file);
    }
    return new FileInputStreamInitData(file, span, delegate);
  }

  private static FileInputStreamInitData init(
    final @NotNull FileDescriptor fd,
    @Nullable FileInputStream delegate
  ) {
    final ISpan span = FileIOSpanManager.startSpan("file.read");
    if (delegate == null) {
      delegate = new FileInputStream(fd);
    }
    // TODO: it's only possible to get filename from FileDescriptor via reflection AND when it's
    // running on Android, should we do that?
    return new FileInputStreamInitData(null, span, delegate);
  }

  @Override
  public int read() throws IOException {
    return spanManager.performIO(delegate::read);
  }

  @Override
  public int read(final byte @NotNull [] b) throws IOException {
    return spanManager.performIO(delegate::read);
  }

  @Override
  public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
    return spanManager.performIO(delegate::read);
  }

  @Override
  public long skip(final long n) throws IOException {
    return spanManager.performIO(() -> delegate.skip(n));
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
      return new SentryFileInputStream(init(name != null ? new File(name) : null, delegate));
    }

    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @Nullable File file
    ) throws FileNotFoundException {
      return new SentryFileInputStream(init(file, delegate));
    }

    public static FileInputStream create(
      final @NotNull FileInputStream delegate,
      final @NotNull FileDescriptor descriptor
    ) {
      return new SentryFileInputStream(init(descriptor, delegate), descriptor);
    }
  }
}
