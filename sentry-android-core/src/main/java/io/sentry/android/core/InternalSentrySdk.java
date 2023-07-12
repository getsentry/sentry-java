package io.sentry.android.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import io.sentry.DateUtils;
import io.sentry.HubAdapter;
import io.sentry.ILogger;
import io.sentry.ObjectWriter;
import io.sentry.Scope;
import io.sentry.SentryLevel;
import io.sentry.protocol.App;
import io.sentry.protocol.Device;
import io.sentry.protocol.User;
import io.sentry.util.MapObjectWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class InternalSentrySdk {

  /**
   * @return a copy of the current hub's topmost scope, or null in case the hub is disabled
   */
  @Nullable
  public static Scope getCurrentScope() {
    final @NotNull AtomicReference<Scope> scopeRef = new AtomicReference<>();
    HubAdapter.getInstance().withScope(scopeRef::set);
    return scopeRef.get();
  }

  @NotNull
  public static Map<String, Object> serializeScope(
      @NotNull Context context, @NotNull SentryAndroidOptions options, @Nullable Scope scope) {
    final @NotNull Map<String, Object> data = new HashMap<>();
    if (scope == null) {
      return data;
    }

    final @NotNull ILogger logger = options.getLogger();
    final @NotNull ObjectWriter writer = new MapObjectWriter(data);

    try {

      final @NotNull DeviceInfoUtil deviceInfoUtil = DeviceInfoUtil.getInstance(context, options);
      final @NotNull Device deviceInfo = deviceInfoUtil.collectDeviceInformation(false, false);
      scope.getContexts().setDevice(deviceInfo);
      scope.getContexts().setOperatingSystem(deviceInfoUtil.getOperatingSystem());

      // user
      @Nullable User user = scope.getUser();
      if (user == null) {
        user = new User();
        scope.setUser(user);
      }
      if (user.getId() == null) {
        user.setId(Installation.id(context));
      }

      // app context
      @Nullable App app = scope.getContexts().getApp();
      if (app == null) {
        app = new App();
        app.setAppName(ContextUtils.getApplicationName(context, options.getLogger()));
        app.setAppStartTime(DateUtils.toUtilDate(AppStartState.getInstance().getAppStartTime()));

        final @NotNull BuildInfoProvider buildInfoProvider =
            new BuildInfoProvider(options.getLogger());
        final @Nullable PackageInfo packageInfo =
            ContextUtils.getPackageInfo(
                context, PackageManager.GET_PERMISSIONS, options.getLogger(), buildInfoProvider);
        if (packageInfo != null) {
          ContextUtils.setAppPackageInfo(packageInfo, buildInfoProvider, app);
        }
        scope.getContexts().setApp(app);
      }

      writer.name("user").value(logger, scope.getUser());
      writer.name("contexts").value(logger, scope.getContexts());
      writer.name("tags").value(logger, scope.getTags());
      writer.name("extras").value(logger, scope.getExtras());
      writer.name("fingerprint").value(logger, scope.getFingerprint());
      writer.name("level").value(logger, scope.getLevel());
      writer.name("breadcrumbs").value(logger, scope.getBreadcrumbs());
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Could not serialize scope.", e);
      return new HashMap<>();
    }

    return data;
  }
}
