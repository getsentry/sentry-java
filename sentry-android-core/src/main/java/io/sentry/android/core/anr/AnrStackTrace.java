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

  private static final int MAX_STACK_LENGTH = 1000;

  public final StackTraceElement[] stack;
  public final long timestampMs;
  public final @Nullable Thread.State state;

  public AnrStackTrace(final long timestampMs, final StackTraceElement[] stack) {
    this(timestampMs, stack, null);
  }

  public AnrStackTrace(
      final long timestampMs, final StackTraceElement[] stack, final @Nullable Thread.State state) {
    this.timestampMs = timestampMs;
    this.stack = stack;
    this.state = state;
  }

  @Override
  public int compareTo(final @NotNull AnrStackTrace o) {
    return Long.compare(timestampMs, o.timestampMs);
  }

  /**
   * Writes this sample in format version 2. All numbers are big-endian, and strings use the
   * modified UTF-8 of {@link DataOutputStream#writeUTF(String)}.
   *
   * <pre>
   * short   version (2)
   * long    timestampMs
   * boolean isStateNull
   * UTF     state name, or "" if isStateNull
   * int     number of frames
   * for each frame:
   *   UTF     className
   *   UTF     methodName
   *   boolean isFileNameNull
   *   UTF     fileName, or "" if isFileNameNull
   *   int     lineNumber
   * </pre>
   *
   * <p>Version 1 is the same without the two state fields. {@link #deserialize(DataInputStream)}
   * reads both versions.
   */
  public void serialize(final @NotNull DataOutputStream dos) throws IOException {
    dos.writeShort(2); // version
    dos.writeLong(timestampMs);
    dos.writeBoolean(state == null);
    dos.writeUTF(state == null ? "" : state.name());
    dos.writeInt(stack.length);
    for (final @NotNull StackTraceElement element : stack) {
      dos.writeUTF(StringUtils.getOrEmpty(element.getClassName()));
      dos.writeUTF(StringUtils.getOrEmpty(element.getMethodName()));
      // Write null as a special marker to preserve null vs empty string distinction
      final @Nullable String fileName = element.getFileName();
      dos.writeBoolean(fileName == null);
      dos.writeUTF(fileName == null ? "" : fileName);
      dos.writeInt(element.getLineNumber());
    }
  }

  @Nullable
  public static AnrStackTrace deserialize(final @NotNull DataInputStream dis) throws IOException {
    try {
      final short version = dis.readShort();
      if (version == 1 || version == 2) {
        final long timestampMs = dis.readLong();
        @Nullable Thread.State state = null;
        if (version == 2) {
          final boolean isStateNull = dis.readBoolean();
          final @NotNull String stateName = dis.readUTF();
          if (!isStateNull) {
            try {
              state = Thread.State.valueOf(stateName);
            } catch (IllegalArgumentException e) {
              // written by a runtime with a state this one does not know
            }
          }
        }
        final int stackLength = dis.readInt();
        if (stackLength < 0 || stackLength > MAX_STACK_LENGTH) {
          return null;
        }
        final @NotNull StackTraceElement[] stack = new StackTraceElement[stackLength];

        for (int i = 0; i < stackLength; i++) {
          final @NotNull String className = dis.readUTF();
          final @NotNull String methodName = dis.readUTF();
          // Read the null marker to restore null vs empty string distinction
          final boolean isFileNameNull = dis.readBoolean();
          final @NotNull String fileNameStr = dis.readUTF();
          final @Nullable String fileName = isFileNameNull ? null : fileNameStr;
          final int lineNumber = dis.readInt();
          final StackTraceElement element =
              new StackTraceElement(className, methodName, fileName, lineNumber);
          stack[i] = element;
        }

        return new AnrStackTrace(timestampMs, stack, state);
      } else {
        // unsupported future version
        return null;
      }
    } catch (EOFException e) {
      return null;
    }
  }
}
