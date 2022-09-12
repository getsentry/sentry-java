package io.sentry.android.core.internal.measurement.battery;

import io.sentry.ITransaction;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.measurement.BackgroundAwareMeasurementCollector;
import io.sentry.measurement.MeasurementBackgroundService;
import io.sentry.measurement.MeasurementBackgroundServiceType;
import io.sentry.measurement.MeasurementContext;
import io.sentry.protocol.MeasurementValue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class BatteryLevelMeasurementCollector extends BackgroundAwareMeasurementCollector {

  private final @NotNull SentryOptions options;
  private List<MeasurementBackgroundServiceType> listenToTypes;

  public BatteryLevelMeasurementCollector(
      @NotNull SentryOptions options, @NotNull MeasurementBackgroundService backgroundService) {
    super(backgroundService);
    this.options = options;
    this.listenToTypes = Arrays.asList(MeasurementBackgroundServiceType.BATTERY);
  }

  @Override
  protected List<MeasurementBackgroundServiceType> listenToTypes() {
    return listenToTypes;
  }

  @Override
  public void onTransactionStartedInternal(@NotNull ITransaction transaction) {
    // nothing to do
  }

  @Override
  public @Nullable Map<String, MeasurementValue> onTransactionFinishedInternal(
      @NotNull ITransaction transaction, @NotNull MeasurementContext context) {
    @NotNull Map<String, MeasurementValue> results = new HashMap<>();

    List<Object> batteryLevels =
        backgroundService.getFrom(
            MeasurementBackgroundServiceType.BATTERY,
            startDate,
            backgroundService.getPollingInterval());
    if (batteryLevels.size() >= 2) {
      float batteryLevelAtStart = (float) batteryLevels.get(0);
      float batteryLevelAtEnd = (float) batteryLevels.get(batteryLevels.size() - 1);
      float batteryLevelDelta = batteryLevelAtEnd - batteryLevelAtStart;
      results.put("battery_drain", new MeasurementValue(batteryLevelDelta));
      @Nullable Double transactionDuration = context.getDuration();
      if (transactionDuration != null) {
        @NotNull Double batteryDrainPerSecond = batteryLevelDelta / transactionDuration;
        results.put(
            "battery_drain_per_second", new MeasurementValue(batteryDrainPerSecond.floatValue()));
      }
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Did not get enough battery level background measurement values to calculate something.");
    }
    return results;
  }
}
