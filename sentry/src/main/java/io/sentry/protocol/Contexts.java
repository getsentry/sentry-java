package io.sentry.protocol;

import com.jakewharton.nopen.annotation.Open;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

@Open
public class Contexts extends ConcurrentHashMap<String, Object> implements Cloneable {
  private static final long serialVersionUID = 252445813254943011L;

  protected <T> T toContextType(String key, Class<T> clazz) {
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

  public SentryRuntime getRuntime() {
    return toContextType(SentryRuntime.TYPE, SentryRuntime.class);
  }

  public void setRuntime(SentryRuntime runtime) {
    this.put(SentryRuntime.TYPE, runtime);
  }

  public Gpu getGpu() {
    return toContextType(Gpu.TYPE, Gpu.class);
  }

  public void setGpu(Gpu gpu) {
    this.put(Gpu.TYPE, gpu);
  }

  @Override
  public @NotNull Contexts clone() throws CloneNotSupportedException {
    final Contexts clone = new Contexts();
    cloneInto(clone);
    return clone;
  }

  protected void cloneInto(Contexts clone) throws CloneNotSupportedException {
    for (Map.Entry<String, Object> entry : entrySet()) {
      if (entry != null) {
        Object value = entry.getValue();
        if (App.TYPE.equals(entry.getKey()) && value instanceof App) {
          clone.setApp(((App) value).clone());
        } else if (Browser.TYPE.equals(entry.getKey()) && value instanceof Browser) {
          clone.setBrowser(((Browser) value).clone());
        } else if (Device.TYPE.equals(entry.getKey()) && value instanceof Device) {
          clone.setDevice(((Device) value).clone());
        } else if (OperatingSystem.TYPE.equals(entry.getKey())
            && value instanceof OperatingSystem) {
          clone.setOperatingSystem(((OperatingSystem) value).clone());
        } else if (SentryRuntime.TYPE.equals(entry.getKey()) && value instanceof SentryRuntime) {
          clone.setRuntime(((SentryRuntime) value).clone());
        } else if (Gpu.TYPE.equals(entry.getKey()) && value instanceof Gpu) {
          clone.setGpu(((Gpu) value).clone());
        } else {
          clone.put(entry.getKey(), value);
        }
      }
    }
  }
}
