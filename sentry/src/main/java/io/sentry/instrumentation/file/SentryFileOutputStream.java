package io.sentry.instrumentation.file;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ISpan;
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
 * Note, that span is started when this OutputStream is instantiated via constructor and finishes
 * when the {@link java.io.FileOutputStream#close()} is called.
 */
@SuppressWarnings("unused") // used via bytecode manipulation (SAGP)
@Open
public class SentryFileOutputStream extends FileOutputStream {

  private final @NotNull FileOutputStream delegate;
  private final @NotNull FileIOSpanManager spanManager;

  public SentryFileOutputStream(final @Nullable String name) throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, false, null));
  }

  public SentryFileOutputStream(final @Nullable String name, final boolean append)
    throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, append, null));
  }

  public SentryFileOutputStream(final @Nullable File file) throws FileNotFoundException {
    this(init(file, false, null));
  }

  public SentryFileOutputStream(final @Nullable File file, final boolean append)
    throws FileNotFoundException {
    this(init(file, append, null));
  }

  public SentryFileOutputStream(final @NotNull FileDescriptor fdObj) {
    this(init(fdObj, null), fdObj);
  }

  private SentryFileOutputStream(
    final @NotNull FileOutputStreamInitData data,
    final @NotNull FileDescriptor fd
  ) {
    super(fd);
    spanManager = new FileIOSpanManager(data.span, data.file);
    delegate = data.delegate;
  }

  private SentryFileOutputStream(
    final @NotNull FileOutputStreamInitData data
  ) throws FileNotFoundException {
    super(data.file, data.append);
    spanManager = new FileIOSpanManager(data.span, data.file);
    delegate = data.delegate;
  }

  private static FileOutputStreamInitData init(
    final @Nullable File file,
    final boolean append,
    @Nullable FileOutputStream delegate
  ) throws FileNotFoundException {
    final ISpan span = FileIOSpanManager.startSpan("file.write");
    if (delegate == null) {
      delegate = new FileOutputStream(file);
    }
    return new FileOutputStreamInitData(file, append, span, delegate);
  }

  private static FileOutputStreamInitData init(
    final @NotNull FileDescriptor fd,
    @Nullable FileOutputStream delegate
  ) {
    final ISpan span = FileIOSpanManager.startSpan("file.write");
    if (delegate == null) {
      delegate = new FileOutputStream(fd);
    }
    // TODO: it's only possible to get filename from FileDescriptor via reflection AND when it's
    // running on Android, should we do that?
    return new FileOutputStreamInitData(null, false, span, delegate);
  }

  @Override public void write(final int b) throws IOException {
    spanManager.performIO(() -> {
      delegate.write(b);
      return 1;
    });
  }

  @Override public void write(final byte @NotNull [] b) throws IOException {
    spanManager.performIO(() -> {
      delegate.write(b);
      return b.length;
    });
  }

  @Override public void write(final byte @NotNull [] b, final int off, final int len)
    throws IOException {
    spanManager.performIO(() -> {
      delegate.write(b, off, len);
      return len;
    });
  }

  @Override public void close() throws IOException {
    spanManager.finish(delegate);
  }

  public final static class Factory {
    public static FileOutputStream create(
      final @NotNull FileOutputStream delegate,
      final @Nullable String name
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(
        init(name != null ? new File(name) : null, false, delegate));
    }

    public static FileOutputStream create(
      final @NotNull FileOutputStream delegate,
      final @Nullable String name,
      final boolean append
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(
        init(name != null ? new File(name) : null, append, delegate));
    }

    public static FileOutputStream create(
      final @NotNull FileOutputStream delegate,
      final @Nullable File file
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(init(file, false, delegate));
    }

    public static FileOutputStream create(
      final @NotNull FileOutputStream delegate,
      final @Nullable File file,
      final boolean append
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(init(file, append, delegate));
    }

    public static FileOutputStream create(
      final @NotNull FileOutputStream delegate,
      final @NotNull FileDescriptor fdObj
    ) {
      return new SentryFileOutputStream(init(fdObj, delegate), fdObj);
    }
  }
}
