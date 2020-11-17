package io.sentry.protocol;

import com.google.gson.annotations.SerializedName;
import io.sentry.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holds information about a single stacktrace frame.
 *
 * <p>Each object should contain **at least** a `filename`, `function` or `instruction_addr`
 * attribute. All values are optional, but recommended.
 */
public final class SentryStackFrame implements IUnknownPropertiesConsumer {
  /** Source code leading up to `lineno`. */
  private List<String> preContext;
  /** Source code of the lines after `lineno`. */
  private List<String> postContext;
  /** Mapping of local variables and expression names that were available in this frame. */
  private Map<String, String> vars;

  private List<Integer> framesOmitted;
  /** The source file name (basename only). */
  private String filename;
  /**
   * Name of the frame's function. This might include the name of a class.
   *
   * <p>This function name may be shortened or demangled. If not, Sentry will demangle and shorten
   * it for some platforms. The original function name will be stored in `raw_function`.
   */
  private String function;
  /**
   * Name of the module the frame is contained in.
   *
   * <p>Note that this might also include a class name if that is something the language natively
   * considers to be part of the stack (for instance in Java).
   */
  private String module;
  /** Line number within the source file, starting at 1. */
  private Integer lineno;
  /** Column number within the source file, starting at 1. */
  private Integer colno;
  /** Absolute path to the source file. */
  private String absPath;
  /** Source code of the current line (`lineno`). */
  private String contextLine;
  /**
   * Override whether this frame should be considered part of application code, or part of
   * libraries/frameworks/dependencies.
   *
   * <p>Setting this attribute to `false` causes the frame to be hidden/collapsed by default and
   * mostly ignored during issue grouping.
   */
  private Boolean inApp;

  @SerializedName(value = "package")
  private String _package;

  @SerializedName(value = "native")
  private Boolean _native;

  /**
   * Which platform this frame is from.
   *
   * <p>This can override the platform for a single frame. Otherwise, the platform of the event is
   * assumed. This can be used for multi-platform stack traces, such as in React Native.
   */
  private String platform;

  /** (C/C++/Native) Start address of the containing code module (image). */
  private String imageAddr;
  /**
   * (C/C++/Native) Start address of the frame's function.
   *
   * <p>We use the instruction address for symbolication, but this can be used to calculate an
   * instruction offset automatically.
   */
  private String symbolAddr;
  /**
   * (C/C++/Native) An optional instruction address for symbolication.
   *
   * <p>This should be a string with a hexadecimal number that includes a 0x prefix. If this is set
   * and a known image is defined in the [Debug Meta Interface]({%- link
   * _documentation/development/sdk-dev/event-payloads/debugmeta.md -%}), then symbolication can
   * take place.
   */
  private String instructionAddr;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

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
  private String rawFunction;

  public List<String> getPreContext() {
    return preContext;
  }

  public void setPreContext(List<String> preContext) {
    this.preContext = preContext;
  }

  public List<String> getPostContext() {
    return postContext;
  }

  public void setPostContext(List<String> postContext) {
    this.postContext = postContext;
  }

  public Map<String, String> getVars() {
    return vars;
  }

  public void setVars(Map<String, String> vars) {
    this.vars = vars;
  }

  public List<Integer> getFramesOmitted() {
    return framesOmitted;
  }

  public void setFramesOmitted(List<Integer> framesOmitted) {
    this.framesOmitted = framesOmitted;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getFunction() {
    return function;
  }

  public void setFunction(String function) {
    this.function = function;
  }

  public String getModule() {
    return module;
  }

  public void setModule(String module) {
    this.module = module;
  }

  public Integer getLineno() {
    return lineno;
  }

  public void setLineno(Integer lineno) {
    this.lineno = lineno;
  }

  public Integer getColno() {
    return colno;
  }

  public void setColno(Integer colno) {
    this.colno = colno;
  }

  public String getAbsPath() {
    return absPath;
  }

  public void setAbsPath(String absPath) {
    this.absPath = absPath;
  }

  public String getContextLine() {
    return contextLine;
  }

  public void setContextLine(String contextLine) {
    this.contextLine = contextLine;
  }

  public Boolean isInApp() {
    return inApp;
  }

  public void setInApp(Boolean inApp) {
    this.inApp = inApp;
  }

  public String getPackage() {
    return _package;
  }

  public void setPackage(String _package) {
    this._package = _package;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getImageAddr() {
    return imageAddr;
  }

  public void setImageAddr(String imageAddr) {
    this.imageAddr = imageAddr;
  }

  public String getSymbolAddr() {
    return symbolAddr;
  }

  public void setSymbolAddr(String symbolAddr) {
    this.symbolAddr = symbolAddr;
  }

  public String getInstructionAddr() {
    return instructionAddr;
  }

  public void setInstructionAddr(String instructionAddr) {
    this.instructionAddr = instructionAddr;
  }

  public Boolean isNative() {
    return _native;
  }

  public void setNative(Boolean _native) {
    this._native = _native;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public String getRawFunction() {
    return rawFunction;
  }

  public void setRawFunction(String rawFunction) {
    this.rawFunction = rawFunction;
  }
}
