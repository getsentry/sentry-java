package io.sentry.time;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Clock implementation where time doesn't move unless it is manually adjusted.
 * Useful for mocking time in tests.
 */
public class FixedClock implements Clock {
    private Date date;

    /**
     * Construct a FixedClock with the given date.
     *
     * @param date Date to set the FixedClock to.
     */
    public FixedClock(Date date) {
        this.date = date;
    }

    @Override
    public long millis() {
        return date.getTime();
    }

    @Override
    public Date date() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * Adjust the FixedClock by the given amount.
     *
     * @param duration Duration of time to adjust the clock by.
     * @param unit Unit of time to adjust the clock by.
     */
    public void tick(long duration, TimeUnit unit) {
        this.date = new Date(date.getTime() + unit.toMillis(duration));
    }
}
