package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;

/**
 * Decide whether {@link Event}s should be sent to the server by applying a
 * sample rate to random bits from the Event's ID.
 */
public class RandomEventSampler implements EventSampler {
    private static final int RATE_MULTIPLIER = 100;

    private int sampleRate;

    /**
     * Construct a RandomEventSampler with the given sampleRate (from 0.0 to 1.0).
     *
     * @param sampleRate ratio of events to allow through to the server (from 0.0 to 1.0).
     */
    public RandomEventSampler(double sampleRate) {
        this.sampleRate = (int) (sampleRate * RATE_MULTIPLIER);
    }

    /**
     * Handles event sampling logic. The least significant bits (long) of the Event
     * UUID is used because it's a repeatable (for tests) and understandable random
     * number attached to every event.
     *
     * @param event Event to be checked against the sampling logic.
     * @return True if the event should be sent to the server, else False.
     */
    @Override
    public boolean shouldSendEvent(Event event) {
        return event.getId().getLeastSignificantBits() % RATE_MULTIPLIER < sampleRate;
    }
}
