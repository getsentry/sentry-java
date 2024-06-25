package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLockReason;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds information about a single stacktrace frame.
 *
 * <p>Each object should contain **at least** a `filename`, `function` or `instruction_addr`
 * attribute. All values are optional, but recommended.
 */
public final class SentryStackFrame implements JsonUnknown, JsonSerializable {
  /** Source code leading up to `lineno`. */
  private @Nullable List<String> preContext;
  /** Source code of the lines after `lineno`. */
  private @Nullable List<String> postContext;
  /** Mapping of local variables and expression names that were available in this frame. */
  private @Nullable Map<String, String> vars;

  private @Nullable List<Integer> framesOmitted;
  /** The source file name (basename only). */
  private @Nullable String filename;
  /**
   * Name of the frame's function. This might include the name of a class.
   *
   * <p>This function name may be shortened or demangled. If not, Sentry will demangle and shorten
   * it for some platforms. The original function name will be stored in `raw_function`.
   */
  private @Nullable String function;
  /**
   * Name of the module the frame is contained in.
   *
   * <p>Note that this might also include a class name if that is something the language natively
   * considers to be part of the stack (for instance in Java).
   */
  private @Nullable String module;
  /** Line number within the source file, starting at 1. */
  private @Nullable Integer lineno;
  /** Column number within the source file, starting at 1. */
  private @Nullable Integer colno;
  /** Absolute path to the source file. */
  private @Nullable String absPath;
  /** Source code of the current line (`lineno`). */
  private @Nullable String contextLine;
  /**
   * Override whether this frame should be considered part of application code, or part of
   * libraries/frameworks/dependencies.
   *
   * <p>Setting this attribute to `false` causes the frame to be hidden/collapsed by default and
   * mostly ignored during issue grouping.
   */
  private @Nullable Boolean inApp;

  private @Nullable String _package;

  private @Nullable Boolean _native;

  /**
   * Which platform this frame is from.
   *
   * <p>This can override the platform for a single frame. Otherwise, the platform of the event is
   * assumed. This can be used for multi-platform stack traces, such as in React Native.
   */
  private @Nullable String platform;

  /** (C/C++/Native) Start address of the containing code module (image). */
  private @Nullable String imageAddr;
  /**
   * (C/C++/Native) Start address of the frame's function.
   *
   * <p>We use the instruction address for symbolication, but this can be used to calculate an
   * instruction offset automatically.
   */
  private @Nullable String symbolAddr;
  /**
   * (C/C++/Native) An optional instruction address for symbolication.
   *
   * <p>This should be a string with a hexadecimal number that includes a 0x prefix. If this is set
   * and a known image is defined in the [Debug Meta Interface]({%- link
   * _documentation/development/sdk-dev/event-payloads/debugmeta.md -%}), then symbolication can
   * take place.
   */
  private @Nullable String instructionAddr;

  /**
   * Potentially mangled name of the symbol as it appears in an executable.
   *
   * <p>This is different from a function name by generally being the mangled name that appears
   * natively in the binary. This is relevant for languages like Swift, C++ or Rust.
   */
  private @Nullable String symbol;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  /**
   * A raw (but potentially truncated) function value.
   *
   * <p>The original function name, if the function name is shortened or demangled. Sentry shows the
   * raw function when clicking on the shortened one in the UI.
   *
   * <p>If this has the same value as `function` it's best to be omitted. This exists because on
   * many platforms the function itself contains additional information like overload specifies or a
   * lot of generics which can make it exceed the maximum limit we provide for the field. In those
   * cases then we cannot reliably trim down the function any more at a later point because the more
   * valuable information has been removed.
   *
   * <p>The logic to be applied is that an intelligently trimmed function name should be stored in
   * `function` and the value before trimming is stored in this field instead. However also this
   * field will be capped at 256 characters at the moment which often means that not the entire
   * original value can be stored.
   */
  private @Nullable String rawFunction;

  /** Represents a lock (java monitor object) held by this frame. */
  private @Nullable SentryLockReason lock;

  public @Nullable List<String> getPreContext() {
    return preContext;
  }

  public void setPreContext(final @Nullable List<String> preContext) {
    this.preContext = preContext;
  }

  public @Nullable List<String> getPostContext() {
    return postContext;
  }

  public void setPostContext(final @Nullable List<String> postContext) {
    this.postContext = postContext;
  }

  public @Nullable Map<String, String> getVars() {
    return vars;
  }

  public void setVars(final @Nullable Map<String, String> vars) {
    this.vars = vars;
  }

  public @Nullable List<Integer> getFramesOmitted() {
    return framesOmitted;
  }

  public void setFramesOmitted(final @Nullable List<Integer> framesOmitted) {
    this.framesOmitted = framesOmitted;
  }

  public @Nullable String getFilename() {
    return filename;
  }

  public void setFilename(final @Nullable String filename) {
    this.filename = filename;
  }

  public @Nullable String getFunction() {
    return function;
  }

  public void setFunction(final @Nullable String function) {
    this.function = function;
  }

  public @Nullable String getModule() {
    return module;
  }

  public void setModule(final @Nullable String module) {
    this.module = module;
  }

  public @Nullable Integer getLineno() {
    return lineno;
  }

  public void setLineno(final @Nullable Integer lineno) {
    this.lineno = lineno;
  }

  public @Nullable Integer getColno() {
    return colno;
  }

  public void setColno(final @Nullable Integer colno) {
    this.colno = colno;
  }

  public @Nullable String getAbsPath() {
    return absPath;
  }

  public void setAbsPath(final @Nullable String absPath) {
    this.absPath = absPath;
  }

  public @Nullable String getContextLine() {
    return contextLine;
  }

  public void setContextLine(final @Nullable String contextLine) {
    this.contextLine = contextLine;
  }

  public @Nullable Boolean isInApp() {
    return inApp;
  }

  public void setInApp(final @Nullable Boolean inApp) {
    this.inApp = inApp;
  }

  public @Nullable String getPackage() {
    return _package;
  }

  public void setPackage(final @Nullable String _package) {
    this._package = _package;
  }

  public @Nullable String getPlatform() {
    return platform;
  }

  public void setPlatform(final @Nullable String platform) {
    this.platform = platform;
  }

  public @Nullable String getImageAddr() {
    return imageAddr;
  }

  public void setImageAddr(final @Nullable String imageAddr) {
    this.imageAddr = imageAddr;
  }

  public @Nullable String getSymbolAddr() {
    return symbolAddr;
  }

  public void setSymbolAddr(final @Nullable String symbolAddr) {
    this.symbolAddr = symbolAddr;
  }

  public @Nullable String getInstructionAddr() {
    return instructionAddr;
  }

  public void setInstructionAddr(final @Nullable String instructionAddr) {
    this.instructionAddr = instructionAddr;
  }

  public @Nullable Boolean isNative() {
    return _native;
  }

  public void setNative(final @Nullable Boolean _native) {
    this._native = _native;
  }

  public @Nullable String getRawFunction() {
    return rawFunction;
  }

  public void setRawFunction(final @Nullable String rawFunction) {
    this.rawFunction = rawFunction;
  }

  @Nullable
  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(final @Nullable String symbol) {
    this.symbol = symbol;
  }

  @Nullable
  public SentryLockReason getLock() {
    return lock;
  }

  public void setLock(final @Nullable SentryLockReason lock) {
    this.lock = lock;
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

  public static final class JsonKeys {
    public static final String FILENAME = "filename";
    public static final String FUNCTION = "function";
    public static final String MODULE = "module";
    public static final String LINENO = "lineno";
    public static final String COLNO = "colno";
    public static final String ABS_PATH = "abs_path";
    public static final String CONTEXT_LINE = "context_line";
    public static final String IN_APP = "in_app";
    public static final String PACKAGE = "package";
    public static final String NATIVE = "native";
    public static final String PLATFORM = "platform";
    public static final String IMAGE_ADDR = "image_addr";
    public static final String SYMBOL_ADDR = "symbol_addr";
    public static final String INSTRUCTION_ADDR = "instruction_addr";
    public static final String RAW_FUNCTION = "raw_function";
    public static final String SYMBOL = "symbol";
    public static final String LOCK = "lock";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (filename != null) {
      writer.name(JsonKeys.FILENAME).value(filename);
    }
    if (function != null) {
      writer.name(JsonKeys.FUNCTION).value(function);
    }
    if (module != null) {
      writer.name(JsonKeys.MODULE).value(module);
    }
    if (lineno != null) {
      writer.name(JsonKeys.LINENO).value(lineno);
    }
    if (colno != null) {
      writer.name(JsonKeys.COLNO).value(colno);
    }
    if (absPath != null) {
      writer.name(JsonKeys.ABS_PATH).value(absPath);
    }
    if (contextLine != null) {
      writer.name(JsonKeys.CONTEXT_LINE).value(contextLine);
    }
    if (inApp != null) {
      writer.name(JsonKeys.IN_APP).value(inApp);
    }
    if (_package != null) {
      writer.name(JsonKeys.PACKAGE).value(_package);
    }
    if (_native != null) {
      writer.name(JsonKeys.NATIVE).value(_native);
    }
    if (platform != null) {
      writer.name(JsonKeys.PLATFORM).value(platform);
    }
    if (imageAddr != null) {
      writer.name(JsonKeys.IMAGE_ADDR).value(imageAddr);
    }
    if (symbolAddr != null) {
      writer.name(JsonKeys.SYMBOL_ADDR).value(symbolAddr);
    }
    if (instructionAddr != null) {
      writer.name(JsonKeys.INSTRUCTION_ADDR).value(instructionAddr);
    }
    if (rawFunction != null) {
      writer.name(JsonKeys.RAW_FUNCTION).value(rawFunction);
    }
    if (symbol != null) {
      writer.name(JsonKeys.SYMBOL).value(symbol);
    }
    if (lock != null) {
      writer.name(JsonKeys.LOCK).value(logger, lock);
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

  public static final class Deserializer implements JsonDeserializer<SentryStackFrame> {
    @Override
    public @NotNull SentryStackFrame deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      SentryStackFrame sentryStackFrame = new SentryStackFrame();
      Map<String, Object> unknown = null;
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.FILENAME:
            sentryStackFrame.filename = reader.nextStringOrNull();
            break;
          case JsonKeys.FUNCTION:
            sentryStackFrame.function = reader.nextStringOrNull();
            break;
          case JsonKeys.MODULE:
            sentryStackFrame.module = reader.nextStringOrNull();
            break;
          case JsonKeys.LINENO:
            sentryStackFrame.lineno = reader.nextIntegerOrNull();
            break;
          case JsonKeys.COLNO:
            sentryStackFrame.colno = reader.nextIntegerOrNull();
            break;
          case JsonKeys.ABS_PATH:
            sentryStackFrame.absPath = reader.nextStringOrNull();
            break;
          case JsonKeys.CONTEXT_LINE:
            sentryStackFrame.contextLine = reader.nextStringOrNull();
            break;
          case JsonKeys.IN_APP:
            sentryStackFrame.inApp = reader.nextBooleanOrNull();
            break;
          case JsonKeys.PACKAGE:
            sentryStackFrame._package = reader.nextStringOrNull();
            break;
          case JsonKeys.NATIVE:
            sentryStackFrame._native = reader.nextBooleanOrNull();
            break;
          case JsonKeys.PLATFORM:
            sentryStackFrame.platform = reader.nextStringOrNull();
            break;
          case JsonKeys.IMAGE_ADDR:
            sentryStackFrame.imageAddr = reader.nextStringOrNull();
            break;
          case JsonKeys.SYMBOL_ADDR:
            sentryStackFrame.symbolAddr = reader.nextStringOrNull();
            break;
          case JsonKeys.INSTRUCTION_ADDR:
            sentryStackFrame.instructionAddr = reader.nextStringOrNull();
            break;
          case JsonKeys.RAW_FUNCTION:
            sentryStackFrame.rawFunction = reader.nextStringOrNull();
            break;
          case JsonKeys.SYMBOL:
            sentryStackFrame.symbol = reader.nextStringOrNull();
            break;
          case JsonKeys.LOCK:
            sentryStackFrame.lock = reader.nextOrNull(logger, new SentryLockReason.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      sentryStackFrame.setUnknown(unknown);
      reader.endObject();
      return sentryStackFrame;
    }
  }

  // endregion
}
