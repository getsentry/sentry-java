package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A stack trace of a single thread.
 *
 * <p>A stack trace contains a list of frames, each with various bits (most optional) describing the
 * context of that frame. Frames should be sorted from oldest to newest.
 *
 * <p>For the given example program written in Python:
 *
 * <p>```python def foo(): my_var = 'foo' raise ValueError()
 *
 * <p>def main(): foo() ```
 *
 * <p>A minimalistic stack trace for the above program in the correct order:
 *
 * <p>```json { "frames": [ {"function": "main"}, {"function": "foo"} ] } ```
 *
 * <p>The top frame fully symbolicated with five lines of source context:
 *
 * <p>```json { "frames": [{ "in_app": true, "function": "myfunction", "abs_path":
 * "/real/file/name.py", "filename": "file/name.py", "lineno": 3, "vars": { "my_var": "'value'" },
 * "pre_context": [ "def foo():", " my_var = 'foo'", ], "context_line": " raise ValueError()",
 * "post_context": [ "", "def main():" ], }] } ```
 *
 * <p>A minimal native stack trace with register values. Note that the `package` event attribute
 * must be "native" for these frames to be symbolicated.
 *
 * <p>```json { "frames": [ {"instruction_addr": "0x7fff5bf3456c"}, {"instruction_addr":
 * "0x7fff5bf346c0"}, ], "registers": { "rip": "0x00007ff6eef54be2", "rsp": "0x0000003b710cd9e0" } }
 * ```
 */
public final class SentryStackTrace implements JsonUnknown, JsonSerializable {
  /**
   * Required. A non-empty list of stack frames. The list is ordered from caller to callee, or
   * oldest to youngest. The last frame is the one creating the exception.
   */
  private @Nullable List<SentryStackFrame> frames;

  /**
   * Register values of the thread (top frame).
   *
   * <p>A map of register names and their values. The values should contain the actual register
   * values of the thread, thus mapping to the last frame in the list.
   */
  private @Nullable Map<String, String> registers;

  /**
   * This flag indicates that this stack trace is captured at an arbitrary moment in time and this
   * would affect the quality of grouping, Sentry will special case if this is set to true.
   */
  private @Nullable Boolean snapshot;

  /**
   * This value indicates if, and how, `instruction_addr` values in the stack frames need to be
   * adjusted before they are symbolicated. TODO: should we make this an enum or is a string value
   * fine?
   *
   * @see SentryStackFrame#getInstructionAddr()
   * @see SentryStackFrame#setInstructionAddr(String)
   */
  private @Nullable String instructionAddressAdjustment;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public SentryStackTrace() {}

  public SentryStackTrace(final @Nullable List<SentryStackFrame> frames) {
    this.frames = frames;
  }

  /**
   * Gets the frames of this stacktrace.
   *
   * @return the frames.
   */
  public @Nullable List<SentryStackFrame> getFrames() {
    return frames;
  }

  /**
   * Sets the frames of this stacktrace.
   *
   * @param frames the frames.
   */
  public void setFrames(final @Nullable List<SentryStackFrame> frames) {
    this.frames = frames;
  }

  public @Nullable Map<String, String> getRegisters() {
    return registers;
  }

  public void setRegisters(final @Nullable Map<String, String> registers) {
    this.registers = registers;
  }

  public @Nullable Boolean getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(final @Nullable Boolean snapshot) {
    this.snapshot = snapshot;
  }

  // region json

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public @Nullable String getInstructionAddressAdjustment() {
    return instructionAddressAdjustment;
  }

  public void setInstructionAddressAdjustment(@Nullable String instructionAddressAdjustment) {
    this.instructionAddressAdjustment = instructionAddressAdjustment;
  }

  public static final class JsonKeys {
    public static final String FRAMES = "frames";
    public static final String REGISTERS = "registers";
    public static final String SNAPSHOT = "snapshot";
    public static final String INSTRUCTION_ADDRESS_ADJUSTMENT = "instruction_addr_adjustment";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (frames != null) {
      writer.name(JsonKeys.FRAMES).value(logger, frames);
    }
    if (registers != null) {
      writer.name(JsonKeys.REGISTERS).value(logger, registers);
    }
    if (snapshot != null) {
      writer.name(JsonKeys.SNAPSHOT).value(snapshot);
    }
    if (instructionAddressAdjustment != null) {
      writer.name(JsonKeys.INSTRUCTION_ADDRESS_ADJUSTMENT).value(instructionAddressAdjustment);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<SentryStackTrace> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryStackTrace deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      SentryStackTrace sentryStackTrace = new SentryStackTrace();
      Map<String, Object> unknown = null;
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.FRAMES:
            sentryStackTrace.frames =
                reader.nextListOrNull(logger, new SentryStackFrame.Deserializer());
            break;
          case JsonKeys.REGISTERS:
            sentryStackTrace.registers =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, String>) reader.nextObjectOrNull());
            break;
          case JsonKeys.SNAPSHOT:
            sentryStackTrace.snapshot = reader.nextBooleanOrNull();
            break;
          case JsonKeys.INSTRUCTION_ADDRESS_ADJUSTMENT:
            sentryStackTrace.instructionAddressAdjustment = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      sentryStackTrace.setUnknown(unknown);
      reader.endObject();
      return sentryStackTrace;
    }
  }

  // endregion
}
