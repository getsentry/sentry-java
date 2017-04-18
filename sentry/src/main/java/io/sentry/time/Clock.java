package io.sentry.time;

import java.util.Date;

/**
 * Clock interface, used to inject a Clock dependency so time can be adjusted in tests.
 */
public interface Clock {

    /**
     * Returns the Clock's time in milliseconds.
     *
     * @return the Clock's time in milliseconds.
     */
    long millis();

    /**
     * Returns the Clock's time as a Date object.
     *
     * @return the Clock's time as a Date object.
     */
    Date date();

}
