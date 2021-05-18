package io.sentry.protocol;

import com.google.gson.annotations.SerializedName;
import io.sentry.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds information about a single stacktrace frame.
 *
 * <p>Each object should contain **at least** a `filename`, `function` or `instruction_addr`
 * attribute. All values are optional, but recommended.
 */
public final class SentryStackFrame implements IUnknownPropertiesConsumer {
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

  @SerializedName(value = "package")
  private @Nullable String _package;

  @SerializedName(value = "native")
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

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(final @NotNull Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public @Nullable String getRawFunction() {
    return rawFunction;
  }

  public void setRawFunction(final @Nullable String rawFunction) {
    this.rawFunction = rawFunction;
  }
}
