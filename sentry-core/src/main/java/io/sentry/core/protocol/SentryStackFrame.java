package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;

/** The Sentry stack frame. */
public class SentryStackFrame implements IUnknownPropertiesConsumer {
  private List<String> preContext;
  private List<String> postContext;
  private Map<String, String> vars;
  private List<Integer> framesOmitted;
  private String filename;
  private String function;
  private String module;
  private Integer lineno;
  private Integer colno;
  private String absolutePath;
  private String contextLine;
  private Boolean inApp;
  private Boolean isNative;
  private String _package;
  private String platform;
  private Long imageAddress;
  private Long SymbolAddress;
  private Long instructionOffset;
  private Map<String, Object> unknown;

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

  public String getAbsolutePath() {
    return absolutePath;
  }

  public void setAbsolutePath(String absolutePath) {
    this.absolutePath = absolutePath;
  }

  public String getContextLine() {
    return contextLine;
  }

  public void setContextLine(String contextLine) {
    this.contextLine = contextLine;
  }

  public Boolean getInApp() {
    return inApp;
  }

  public void setInApp(Boolean inApp) {
    this.inApp = inApp;
  }

  public String get_package() {
    return _package;
  }

  public void set_package(String _package) {
    this._package = _package;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public Long getImageAddress() {
    return imageAddress;
  }

  public void setImageAddress(Long imageAddress) {
    this.imageAddress = imageAddress;
  }

  public Long getSymbolAddress() {
    return SymbolAddress;
  }

  public void setSymbolAddress(Long symbolAddress) {
    SymbolAddress = symbolAddress;
  }

  public Long getInstructionOffset() {
    return instructionOffset;
  }

  public void setInstructionOffset(Long instructionOffset) {
    this.instructionOffset = instructionOffset;
  }

  public Boolean getNative() {
    return isNative;
  }

  public void setNative(Boolean isNative) {
    this.isNative = isNative;
  }

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
