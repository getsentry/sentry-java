package io.sentry.instrumentation.file;

import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
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
    this(name != null ? new File(name) : null, false, ScopesAdapter.getInstance());
  }

  public SentryFileOutputStream(final @Nullable String name, final boolean append)
      throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, append, null, ScopesAdapter.getInstance()));
  }

  public SentryFileOutputStream(final @Nullable File file) throws FileNotFoundException {
    this(file, false, ScopesAdapter.getInstance());
  }

  public SentryFileOutputStream(final @Nullable File file, final boolean append)
      throws FileNotFoundException {
    this(init(file, append, null, ScopesAdapter.getInstance()));
  }

  public SentryFileOutputStream(final @NotNull FileDescriptor fdObj) {
    this(init(fdObj, null, ScopesAdapter.getInstance()), fdObj);
  }

  SentryFileOutputStream(
      final @Nullable File file, final boolean append, final @NotNull IScopes scopes)
      throws FileNotFoundException {
    this(init(file, append, null, scopes));
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
      @NotNull IScopes scopes)
      throws FileNotFoundException {
    final ISpan span = FileIOSpanManager.startSpan(scopes, "file.write");
    if (delegate == null) {
      delegate = new FileOutputStream(file, append);
    }
    return new FileOutputStreamInitData(file, append, span, delegate, scopes.getOptions());
  }

  private static FileOutputStreamInitData init(
      final @NotNull FileDescriptor fd,
      @Nullable FileOutputStream delegate,
      @NotNull IScopes scopes) {
    final ISpan span = FileIOSpanManager.startSpan(scopes, "file.write");
    if (delegate == null) {
      delegate = new FileOutputStream(fd);
    }
    return new FileOutputStreamInitData(null, false, span, delegate, scopes.getOptions());
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
      final @NotNull IScopes scopes = ScopesAdapter.getInstance();
      return isTracingEnabled(scopes)
          ? new SentryFileOutputStream(
              init(name != null ? new File(name) : null, false, delegate, scopes))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @Nullable String name, final boolean append)
        throws FileNotFoundException {
      final @NotNull IScopes scopes = ScopesAdapter.getInstance();
      return isTracingEnabled(scopes)
          ? new SentryFileOutputStream(
              init(name != null ? new File(name) : null, append, delegate, scopes))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @Nullable File file)
        throws FileNotFoundException {
      final @NotNull IScopes scopes = ScopesAdapter.getInstance();
      return isTracingEnabled(scopes)
          ? new SentryFileOutputStream(init(file, false, delegate, scopes))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @Nullable File file, final boolean append)
        throws FileNotFoundException {
      final @NotNull IScopes scopes = ScopesAdapter.getInstance();
      return isTracingEnabled(scopes)
          ? new SentryFileOutputStream(init(file, append, delegate, scopes))
          : delegate;
    }

    public static FileOutputStream create(
        final @NotNull FileOutputStream delegate, final @NotNull FileDescriptor fdObj) {
      final @NotNull IScopes scopes = ScopesAdapter.getInstance();
      return isTracingEnabled(scopes)
          ? new SentryFileOutputStream(init(fdObj, delegate, scopes), fdObj)
          : delegate;
    }

    private static boolean isTracingEnabled(final @NotNull IScopes scopes) {
      final @NotNull SentryOptions options = scopes.getOptions();
      return options.isTracingEnabled();
    }
  }
}
