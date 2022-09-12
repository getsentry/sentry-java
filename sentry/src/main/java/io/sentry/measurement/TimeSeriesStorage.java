package io.sentry.measurement;

import io.sentry.CircularFifoQueue;
import io.sentry.DateUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TimeSeriesStorage<E> {

  private final CircularFifoQueue<TimeSeriesEntry<E>> internalStorage;

  public TimeSeriesStorage(final int capacity) {
    this.internalStorage = new CircularFifoQueue<>(capacity);
  }

  public void add(E item) {
    internalStorage.add(new TimeSeriesEntry<E>(DateUtils.getCurrentDateTime(), item));
  }

  //  public @Nullable E getLatest() {
  //    // TODO read config
  //    return getLatest(500);
  //  }

  public int size() {
    return internalStorage.size();
  }

  @SuppressWarnings("JavaUtilDate")
  public @Nullable E getLatest(long maxAgeInMs) {
    @Nullable TimeSeriesEntry<E> item = internalStorage.peekEnd();
    if (item != null) {
      Date currentDate = DateUtils.getCurrentDateTime();
      long threshold = currentDate.getTime() - maxAgeInMs;
      if (item.getDate().getTime() >= threshold) {
        item.getValue();
      }
    }
    return null;
  }

  @SuppressWarnings("JavaUtilDate")
  public @NotNull List<E> getFrom(@NotNull Date minDate, int allowedTimeBeforeMin) {
    List<E> items = new ArrayList<>();
    long minDateCorrected = minDate.getTime() - allowedTimeBeforeMin;

    for (TimeSeriesEntry<E> entry : internalStorage) {
      if (entry.getDate().getTime() >= minDateCorrected) {
        items.add(entry.getValue());
      }
    }

    return items;
  }

  public static final class TimeSeriesEntry<E> {

    private final @NotNull Date date;
    private final @NotNull E value;

    public TimeSeriesEntry(@NotNull Date date, @NotNull E value) {
      this.date = date;
      this.value = value;
    }

    public @NotNull Date getDate() {
      return date;
    }

    public @NotNull E getValue() {
      return value;
    }
  }
}
