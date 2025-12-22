package io.sentry.android.core.internal.util;

import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NativeEventUtils {
  @Nullable
  public static String buildIdToDebugId(final @NotNull String buildId) {
    try {
      // Abuse BigInteger as a hex string parser. Extra byte needed to handle leading zeros.
      final ByteBuffer buf = ByteBuffer.wrap(new BigInteger("10" + buildId, 16).toByteArray());
      buf.get();
      return String.format(
          "%08x-%04x-%04x-%04x-%04x%08x",
          buf.order(ByteOrder.LITTLE_ENDIAN).getInt(),
          buf.getShort(),
          buf.getShort(),
          buf.order(ByteOrder.BIG_ENDIAN).getShort(),
          buf.getShort(),
          buf.getInt());
    } catch (NumberFormatException | BufferUnderflowException e) {
      return null;
    }
  }
}
