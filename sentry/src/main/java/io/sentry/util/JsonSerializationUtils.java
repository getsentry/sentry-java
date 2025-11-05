package io.sentry.util;

import io.sentry.ILogger;
import io.sentry.ISerializer;
import io.sentry.JsonSerializable;
import io.sentry.SentryLevel;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonSerializationUtils {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  public static @NotNull Map<String, Object> calendarToMap(final @NotNull Calendar calendar) {
    final @NotNull Map<String, Object> map = new HashMap<>();

    map.put("year", calendar.get(Calendar.YEAR));
    map.put("month", calendar.get(Calendar.MONTH));
    map.put("dayOfMonth", calendar.get(Calendar.DAY_OF_MONTH));
    map.put("hourOfDay", calendar.get(Calendar.HOUR_OF_DAY));
    map.put("minute", calendar.get(Calendar.MINUTE));
    map.put("second", calendar.get(Calendar.SECOND));

    return map;
  }

  public static @NotNull List<Object> atomicIntegerArrayToList(
      final @NotNull AtomicIntegerArray array) {
    final int numberOfItems = array.length();
    final @NotNull List<Object> list = new ArrayList<>(numberOfItems);
    for (int i = 0; i < numberOfItems; i++) {
      list.add(array.get(i));
    }
    return list;
  }

  public static @Nullable byte[] bytesFrom(
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger,
      final @NotNull JsonSerializable serializable) {
    try {
      try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
          final Writer writer = new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {

        serializer.serialize(serializable, writer);

        return stream.toByteArray();
      }
    } catch (Throwable t) {
      logger.log(SentryLevel.ERROR, "Could not serialize serializable", t);
      return null;
    }
  }

  /**
   * Calculates the size in bytes of a serializable object when serialized to JSON without actually
   * storing the serialized data. This is more memory efficient than {@link #bytesFrom(ISerializer,
   * ILogger, JsonSerializable)} when you only need the size.
   *
   * @param serializer the serializer
   * @param logger the logger
   * @param serializable the serializable object
   * @return the size in bytes, or -1 if serialization fails
   */
  public static long byteSizeOf(
      final @NotNull ISerializer serializer,
      final @NotNull ILogger logger,
      final @Nullable JsonSerializable serializable) {
    if (serializable == null) {
      return 0;
    }
    try {
      final ByteCountingWriter writer = new ByteCountingWriter();
      serializer.serialize(serializable, writer);
      return writer.getByteCount();
    } catch (Throwable t) {
      logger.log(SentryLevel.ERROR, "Could not calculate size of serializable", t);
      return 0;
    }
  }

  /**
   * A Writer that counts the number of bytes that would be written in UTF-8 encoding without
   * actually storing the data.
   */
  private static final class ByteCountingWriter extends Writer {
    private long byteCount = 0L;

    @Override
    public void write(final char[] cbuf, final int off, final int len) {
      for (int i = off; i < off + len; i++) {
        byteCount += utf8ByteCount(cbuf[i]);
      }
    }

    @Override
    public void write(final int c) {
      byteCount += utf8ByteCount((char) c);
    }

    @Override
    public void write(final @NotNull String str, final int off, final int len) {
      for (int i = off; i < off + len; i++) {
        byteCount += utf8ByteCount(str.charAt(i));
      }
    }

    @Override
    public void flush() {
      // Nothing to flush since we don't store data
    }

    @Override
    public void close() {
      // Nothing to close
    }

    public long getByteCount() {
      return byteCount;
    }

    /**
     * Calculates the number of bytes needed to encode a character in UTF-8.
     *
     * @param c the character
     * @return the number of bytes (1-4)
     */
    private static int utf8ByteCount(final char c) {
      if (c <= 0x7F) {
        return 1; // ASCII
      } else if (c <= 0x7FF) {
        return 2; // 2-byte character
      } else if (Character.isSurrogate(c)) {
        return 2; // Surrogate pair, counted as 2 bytes each (total 4 for the pair)
      } else {
        return 3; // 3-byte character
      }
    }
  }
}
