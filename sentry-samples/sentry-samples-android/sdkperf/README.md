This folder contains various artifacts and info related to testing SDK performance and behaviour under different circumstances.

## Perfetto

The `basic.pbtx` file contains a perfetto config which covers some basic data sources and things that you usually would be interested in while experimenting with the SDK.

You can adjust some certain things like `duration_ms` to make the trace last longer or add additional [data sources](https://perfetto.dev/docs/data-sources/atrace).

To run it, ensure you have a device available via `adb` and then run:

```bash
adb shell perfetto \
  -c basic.pbtx --txt \
  -o /data/misc/perfetto-traces/trace
```

And then perform various activities you're interested in. After the trace has finished, you can pull it:

```bash
adb pull /data/misc/perfetto-traces/trace
```

And open it up in https://ui.perfetto.dev/.

## Network Connectivity

Android has a weird behavior which has been fixed in [Android 15](https://cs.android.com/android/_/android/platform/packages/modules/Connectivity/+/2d78124348f4864d054ea7a7b52683d225bd7c1f), where it would queue up pending NetworkCallbacks while an app is being frozen and would deliver **all** of them after the app unfreezes in a quick succession.

Since our SDK is listening to NetworkCallbacks in [AndroidConnectionStatusProvider](../../../sentry-android-core/src/main/java/io/sentry/android/core/internal/util/AndroidConnectionStatusProvider.java) to determine current network connectivity status and to create breadcrumbs, our SDK can be burst with potentially hundreds or thousands of events after hours or days of the hosting app inactivity.

The following steps are necessary to reproduce the issue:

1. Launch the sample app and send it to background
2. Freeze it with `adb shell am freeze --sticky io.sentry.samples.android`
3. Run the `./wifi_flap` script which looses and obtains network connectivity 10 times.
4. Unfreeze the app with `adb shell am unfreeze io.sentry.samples.android`

You can either watch Logcat or better start a Perfetto trace beforehand and then open it and observe the number of binder calls our SDK is doing to the Connectivity service.

### Solution

We have addressed the issue in [#4579](https://github.com/getsentry/sentry-java/pull/4579) by unsubscribing from network connectivity updates when app goes to background and re-subscribing again on foreground.

## System Events

[Android 14](https://developer.android.com/develop/background-work/background-tasks/broadcasts#android-14) introduced a new behavior that defers any system broadcasts while an app is in a cached state. These pending broadcasts will be delivered to the app once it gets uncached in a quick succession.

Our SDK is listening to a bunch of broadcasts in [SystemEventsBreadcrumbsIntegration](../../../sentry-android-core/src/main/java/io/sentry/android/core/SystemEventsBreadcrumbsIntegration.java) to create breadcrumbs, the SDK can be burst with potentially hundreds or thousands of pending broadcasts after hours or days of the hosting app inactivity.

The following steps are necessary to reproduce the issue:

1. Launch the sample app and send it to background
2. Freeze it with `adb shell am freeze --sticky io.sentry.samples.android`
3. Run the `./screen_flap` script which turns the screen on and off 10 times.
4. Unfreeze the app with `adb shell am unfreeze io.sentry.samples.android`

And watch Logcat for a bunch of `SCREEN_OFF`/`SCREEN_ON` breadcrumbs created microseconds apart.

### Solution

We have addressed the issue in [#4338](https://github.com/getsentry/sentry-java/pull/4338) by unregistering the `BroadcastReceiver` when app goes to background and registering it again on foreground.

## App Launch with Background Importance 

While the above two issues can be fixed by observing the App lifecycle, they still may become a problem if the app process has been launched with non-foreground importance (e.g. received a push notification). In this case our SDK would be initialized too and would still subscribe for SystemEvents and NetworkCallbacks while in background.

The following steps are necessary to reproduce the issue:

1. Launch the sample app
2. Kill it with `adb shell am force-stop io.sentry.samples.android`
3. Now launch a dummy service with `adb shell am start-foreground-service -n io.sentry.samples.android/io.sentry.samples.android.DummyService`. This ensures the app process is run with non-foreground importance.
4. Follow any of the steps described in the sections above.

Observe (Logcat or Perfetto) that the faulty behaviours are still reproducible.

### Solution

We have addressed the issue in [#4579](https://github.com/getsentry/sentry-java/pull/4579) by not registering any of the offending integrations when the hosting app process is launched with non-foreground `importance`. We still keep observing the App Lifecycle to ensure we register the integrations when the App has been brought to foreground.