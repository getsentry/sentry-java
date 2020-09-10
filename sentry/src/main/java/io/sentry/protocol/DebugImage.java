package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

public final class DebugImage implements IUnknownPropertiesConsumer {

  private String uuid;
  private String type;
  private String debugId;
  private String debugFile;
  private String codeFile;
  private String imageAddr;
  private Long imageSize;
  private String arch;
  private String codeId;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDebugId() {
    return debugId;
  }

  public void setDebugId(String debugId) {
    this.debugId = debugId;
  }

  public String getDebugFile() {
    return debugFile;
  }

  public void setDebugFile(String debugFile) {
    this.debugFile = debugFile;
  }

  public String getCodeFile() {
    return codeFile;
  }

  public void setCodeFile(String codeFile) {
    this.codeFile = codeFile;
  }

  public String getImageAddr() {
    return imageAddr;
  }

  public void setImageAddr(String imageAddr) {
    this.imageAddr = imageAddr;
  }

  public Long getImageSize() {
    return imageSize;
  }

  public void setImageSize(Long imageSize) {
    this.imageSize = imageSize;
  }

  public String getArch() {
    return arch;
  }

  public void setArch(String arch) {
    this.arch = arch;
  }

  public String getCodeId() {
    return codeId;
  }

  public void setCodeId(String codeId) {
    this.codeId = codeId;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
