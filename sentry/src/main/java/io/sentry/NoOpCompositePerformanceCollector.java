package io.sentry;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpCompositePerformanceCollector implements CompositePerformanceCollector {

  private static final NoOpCompositePerformanceCollector instance =
      new NoOpCompositePerformanceCollector();

  public static NoOpCompositePerformanceCollector getInstance() {
    return instance;
  }

  private NoOpCompositePerformanceCollector() {}

  @Override
  public void start(@NotNull ITransaction transaction) {}

  @Override
  public void start(@NotNull String id) {}

  @Override
  public void onSpanStarted(@NotNull ISpan span) {}

  @Override
  public void onSpanFinished(@NotNull ISpan span) {}

  @Override
  public @Nullable List<PerformanceCollectionData> stop(@NotNull ITransaction transaction) {
    return null;
  }

  @Override
  public @Nullable List<PerformanceCollectionData> stop(@NotNull String id) {
    return null;
  }

  @Override
  public void close() {}
}
