package io.sentry.core.protocol;

import java.util.concurrent.ConcurrentHashMap;

public class Contexts extends ConcurrentHashMap<String, Object> {
  private static final long serialVersionUID = 252445813254943011L;

  <T> T toContextType(String key, Class<T> clazz) {
    Object item = get(key);
    return clazz.isInstance(item) ? clazz.cast(item) : null;
  }

  public App getApp() {
    return toContextType(App.TYPE, App.class);
  }

  public void setApp(App app) {
    this.put(App.TYPE, app);
  }

  public Browser getBrowser() {
    return toContextType(Browser.TYPE, Browser.class);
  }

  public void setBrowser(Browser browser) {
    this.put(Browser.TYPE, browser);
  }

  public Device getDevice() {
    return toContextType(Device.TYPE, Device.class);
  }

  public void setDevice(Device device) {
    this.put(Device.TYPE, device);
  }

  public OperatingSystem getOperatingSystem() {
    return toContextType(OperatingSystem.TYPE, OperatingSystem.class);
  }

  public void setOperatingSystem(OperatingSystem operatingSystem) {
    this.put(OperatingSystem.TYPE, operatingSystem);
  }

  public Runtime getRuntime() {
    return toContextType(Runtime.TYPE, Runtime.class);
  }

  public void setRuntime(Runtime runtime) {
    this.put(Runtime.TYPE, runtime);
  }

  public Gpu getGpu() {
    return toContextType(Gpu.TYPE, Gpu.class);
  }

  public void setGpu(Gpu gpu) {
    this.put(Gpu.TYPE, gpu);
  }
}
