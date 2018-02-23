package io.sentry.connection;

import io.sentry.time.Clock;
import io.sentry.time.SystemClock;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Abstracts the connection lockdown logic (and state) to a single place so that
 * it's easier to understand.
 */
public class LockdownManager {
    /**
     * Default maximum duration for a lockdown.
     */
    public static final long DEFAULT_MAX_LOCKDOWN_TIME = TimeUnit.MINUTES.toMillis(5);
    /**
     * Default base duration for a lockdown.
     */
    public static final long DEFAULT_BASE_LOCKDOWN_TIME = TimeUnit.SECONDS.toMillis(1);
    /**
     * Maximum duration for a lockdown, in milliseconds.
     */
    private long maxLockdownTime = DEFAULT_MAX_LOCKDOWN_TIME;
    /**
     * Base duration for a lockdown, in milliseconds.
     * <p>
     * On each attempt the time is doubled until it reaches {@link #maxLockdownTime}.
     */
    private long baseLockdownTime = DEFAULT_BASE_LOCKDOWN_TIME;
    /**
     * Number of milliseconds after lockdownStartTime to lockdown for, or 0 if not currently locked down.
     */
    private long lockdownTime = 0;
    /**
     * Timestamp of when the current lockdown started, or null if not currently locked down.
     */
    private Date lockdownStartTime = null;
    /**
     * Clock instance used for time, injectable for testing.
     */
    private final Clock clock;

    /**
     * Construct a LockdownManager using the default system clock.
     */
    public LockdownManager() {
        this(new SystemClock());
    }

    /**
     * Construct a LockdownManager using the provided clock.
     *
     * @param clock Clock object to use for lockdown logic
     */
    public LockdownManager(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns true if the system is in a lockdown.
     *
     * @return true if the system is in a lockdown, otherwise false
     */
    public synchronized boolean isLockedDown() {
        return lockdownStartTime != null && (clock.millis() - lockdownStartTime.getTime()) < lockdownTime;
    }

    /**
     * Reset the lockdown state, disabling lockdown and setting the backoff time to zero.
     */
    public synchronized void unlock() {
        lockdownTime = 0;
        lockdownStartTime = null;
    }

    /**
     * Enable lockdown if it's not already enabled, using the recommended time
     * from the provided {@link ConnectionException}, if any.
     *
     * @param connectionException ConnectionException to check for a recommended
     *                            lockdown time, may be null
     * @return whether or not this call actually locked the system down
     */
    public synchronized boolean lockdown(ConnectionException connectionException) {
        // If we are already in a lockdown state, don't change anything
        if (isLockedDown()) {
            return false;
        }

        if (connectionException != null && connectionException.getRecommendedLockdownTime() != null) {
            lockdownTime = connectionException.getRecommendedLockdownTime();
        } else if (lockdownTime != 0) {
            lockdownTime = lockdownTime * 2;
        } else {
            lockdownTime = baseLockdownTime;
        }

        lockdownTime = Math.min(maxLockdownTime, lockdownTime);
        lockdownStartTime = clock.date();

        return true;
    }

    public synchronized void setBaseLockdownTime(long baseLockdownTime) {
        this.baseLockdownTime = baseLockdownTime;
    }

    public synchronized void setMaxLockdownTime(long maxLockdownTime) {
        this.maxLockdownTime = maxLockdownTime;
    }
}
