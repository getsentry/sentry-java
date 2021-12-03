package io.sentry.instrumentation.file;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import io.sentry.util.StringUtils;
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

  private final @Nullable ISpan currentSpan;
  private final @Nullable File file;
  private final @NotNull FileOutputStream delegate;

  private @NotNull SpanStatus spanStatus = SpanStatus.OK;
  private long byteCount;

  public SentryFileOutputStream(@Nullable String name) throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, false, null));
  }

  public SentryFileOutputStream(@Nullable String name, boolean append)
    throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, append, null));
  }

  public SentryFileOutputStream(@Nullable File file) throws FileNotFoundException {
    this(init(file, false, null));
  }

  public SentryFileOutputStream(@Nullable File file, boolean append) throws FileNotFoundException {
    this(init(file, append, null));
  }

  public SentryFileOutputStream(@NotNull FileDescriptor fdObj) {
    this(init(fdObj, null), fdObj);
  }

  private SentryFileOutputStream(
    @NotNull FileOutputStreamInitData data,
    @NotNull FileDescriptor fd
  ) {
    super(fd);
    file = null;
    currentSpan = data.span;
    delegate = data.delegate;
  }

  private SentryFileOutputStream(
    @NotNull FileOutputStreamInitData data
  ) throws FileNotFoundException {
    super(data.file, data.append);
    currentSpan = data.span;
    file = data.file;
    delegate = data.delegate;
  }

  private static FileOutputStreamInitData init(
    @Nullable File file,
    boolean append,
    @Nullable FileOutputStream delegate
  ) throws FileNotFoundException {
    final ISpan span = startSpan();
    if (delegate == null) {
      delegate = new FileOutputStream(file);
    }
    return new FileOutputStreamInitData(file, append, span, delegate);
  }

  private static FileOutputStreamInitData init(
    @NotNull FileDescriptor fd,
    @Nullable FileOutputStream delegate
  ) {
    final ISpan span = startSpan();
    if (delegate == null) {
      delegate = new FileOutputStream(fd);
    }
    // TODO: it's only possible to get filename from FileDescriptor via reflection AND when it's
    // running on Android, should we do that?
    return new FileOutputStreamInitData(null, false, span, delegate);
  }

  private static @Nullable ISpan startSpan() {
    final ISpan parent = Sentry.getSpan();
    return parent != null ? parent.startChild("file.write") : null;
  }

  @Override public void write(int b) throws IOException {
    try {
      delegate.write(b);
      byteCount++;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  @Override public void write(byte @NotNull [] b) throws IOException {
    try {
      delegate.write(b);
      byteCount += b.length;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  @Override public void write(byte @NotNull [] b, int off, int len) throws IOException {
    try {
      delegate.write(b, off, len);
      byteCount += len;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  @Override public void close() throws IOException {
    try {
      delegate.close();
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
    finishSpan();
  }

  private void finishSpan() {
    if (currentSpan != null) {
      if (file != null) {
        String description = file.getName()
          + " "
          + "("
          + StringUtils.byteCountToString(byteCount)
          + ")";
        currentSpan.setDescription(description);
        currentSpan.setData("filepath", file.getAbsolutePath());
      }
      currentSpan.finish(spanStatus);
    }
  }

  public final static class Factory {
    public static FileOutputStream create(
      @NotNull FileOutputStream delegate,
      @Nullable String name
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(
        init(name != null ? new File(name) : null, false, delegate));
    }

    public static FileOutputStream create(
      @NotNull FileOutputStream delegate,
      @Nullable String name,
      boolean append
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(
        init(name != null ? new File(name) : null, append, delegate));
    }

    public static FileOutputStream create(
      @NotNull FileOutputStream delegate,
      @Nullable File file
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(init(file, false, delegate));
    }

    public static FileOutputStream create(
      @NotNull FileOutputStream delegate,
      @Nullable File file,
      boolean append
    ) throws FileNotFoundException {
      return new SentryFileOutputStream(init(file, append, delegate));
    }

    public static FileOutputStream create(@NotNull FileOutputStream delegate,
      @NotNull FileDescriptor fdObj) {
      return new SentryFileOutputStream(init(fdObj, delegate), fdObj);
    }
  }
}
