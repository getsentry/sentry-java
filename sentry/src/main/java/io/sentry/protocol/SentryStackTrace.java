package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
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
public final class SentryStackTrace implements IUnknownPropertiesConsumer {
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

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
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
}
