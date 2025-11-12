package io.sentry.android.core.anr;

import io.sentry.util.StringUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AnrStackTrace implements Comparable<AnrStackTrace> {

  public final StackTraceElement[] stack;
  public final long timestampMs;

  public AnrStackTrace(final long timestampMs, final StackTraceElement[] stack) {
    this.timestampMs = timestampMs;
    this.stack = stack;
  }

  @Override
  public int compareTo(final @NotNull AnrStackTrace o) {
    return Long.compare(timestampMs, o.timestampMs);
  }

  public void serialize(final @NotNull DataOutputStream dos) throws IOException {
    dos.writeShort(1);
    dos.writeLong(timestampMs);
    dos.writeInt(stack.length);
    for (final @NotNull StackTraceElement element : stack) {
      dos.writeUTF(StringUtils.getOrEmpty(element.getClassName()));
      dos.writeUTF(StringUtils.getOrEmpty(element.getMethodName()));
      dos.writeUTF(StringUtils.getOrEmpty(element.getFileName()));
      dos.writeInt(element.getLineNumber());
    }
  }

  @Nullable
  public static AnrStackTrace deserialize(final @NotNull DataInputStream dis) throws IOException {
    try {
      final short version = dis.readShort();
      if (version == 1) {
        final long timestampMs = dis.readLong();
        final int stackLength = dis.readInt();
        final @NotNull StackTraceElement[] stack = new StackTraceElement[stackLength];

        for (int i = 0; i < stackLength; i++) {
          final @NotNull String className = dis.readUTF();
          final @NotNull String methodName = dis.readUTF();
          final @Nullable String fileName = dis.readUTF();
          final int lineNumber = dis.readInt();
          final StackTraceElement element =
              new StackTraceElement(className, methodName, fileName, lineNumber);
          stack[i] = element;
        }

        return new AnrStackTrace(timestampMs, stack);
      } else {
        // unsupported future version
        return null;
      }
    } catch (EOFException e) {
      return null;
    }
  }
}
