package io.sentry.instrumentation.file;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import io.sentry.util.StringUtils;
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
@SuppressWarnings("unused") // used via bytecode manipulation (SAGP)
@Open
public class SentryFileInputStream extends FileInputStream {

  private final @Nullable ISpan currentSpan;
  private final @Nullable File file;
  private final @NotNull FileInputStream delegate;

  private @NotNull SpanStatus spanStatus = SpanStatus.OK;
  private long byteCount;

  public SentryFileInputStream(@Nullable String name) throws FileNotFoundException {
    this(init(name != null ? new File(name) : null, null));
  }

  public SentryFileInputStream(@Nullable File file) throws FileNotFoundException {
    this(init(file, null));
  }

  public SentryFileInputStream(@NotNull FileDescriptor fdObj) {
    this(init(fdObj, null), fdObj);
  }

  private SentryFileInputStream(
    @NotNull FileInputStreamInitData data,
    @NotNull FileDescriptor fd
  ) {
    super(fd);
    file = null;
    currentSpan = data.span;
    delegate = data.delegate;
  }

  private SentryFileInputStream(
    @NotNull FileInputStreamInitData data
  ) throws FileNotFoundException {
    super(data.file);
    currentSpan = data.span;
    file = data.file;
    delegate = data.delegate;
  }

  private static FileInputStreamInitData init(
    @Nullable File file,
    @Nullable FileInputStream delegate
  ) throws FileNotFoundException {
    final ISpan span = startSpan();
    if (delegate == null) {
      delegate = new FileInputStream(file);
    }
    return new FileInputStreamInitData(file, span, delegate);
  }

  private static FileInputStreamInitData init(
    @NotNull FileDescriptor fd,
    @Nullable FileInputStream delegate
  ) {
    final ISpan span = startSpan();
    if (delegate == null) {
      delegate = new FileInputStream(fd);
    }
    // TODO: it's only possible to get filename from FileDescriptor via reflection AND when it's
    // running on Android, should we do that?
    return new FileInputStreamInitData(null, span, delegate);
  }

  private static @Nullable ISpan startSpan() {
    final ISpan parent = Sentry.getSpan();
    return parent != null ? parent.startChild("file.read") : null;
  }

  @Override
  public int read() throws IOException {
    try {
      int result = delegate.read();
      if (result != -1) {
        byteCount++;
      }
      return result;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  @Override
  public int read(byte @NotNull [] b) throws IOException {
    try {
      int result = delegate.read(b);
      if (result != -1) {
        byteCount += result;
      }
      return result;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  @Override
  public int read(byte @NotNull [] b, int off, int len) throws IOException {
    try {
      int result = delegate.read(b, off, len);
      if (result != -1) {
        byteCount += result;
      }
      return result;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  @Override
  public long skip(long n) throws IOException {
    try {
      long result = delegate.skip(n);
      byteCount += result;
      return result;
    } catch (IOException exception) {
      spanStatus = SpanStatus.INTERNAL_ERROR;
      throw exception;
    }
  }

  @Override
  public void close() throws IOException {
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
    public static FileInputStream create(
      @NotNull FileInputStream delegate,
      @Nullable String name
    ) throws FileNotFoundException {
      return new SentryFileInputStream(init(name != null ? new File(name) : null, delegate));
    }

    public static FileInputStream create(
      @NotNull FileInputStream delegate,
      @Nullable File file
    ) throws FileNotFoundException {
      return new SentryFileInputStream(init(file, delegate));
    }

    public static FileInputStream create(
      @NotNull FileInputStream delegate,
      @NotNull FileDescriptor descriptor
    ) {
      return new SentryFileInputStream(init(descriptor, delegate), descriptor);
    }
  }
}
