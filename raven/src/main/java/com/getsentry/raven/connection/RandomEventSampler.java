package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;

import java.util.Random;

/**
 * Decide whether {@link Event}s should be sent to the server by applying a
 * sample rate to random bits from the Event's ID.
 */
public class RandomEventSampler implements EventSampler {
    private static final int RATE_MULTIPLIER = 100;

    private int sampleRate;
    private Random random;

    /**
     * Construct a RandomEventSampler with the given sampleRate (from 0.0 to 1.0).
     *
     * @param sampleRate ratio of events to allow through to the server (from 0.0 to 1.0).
     */
    public RandomEventSampler(double sampleRate) {
        this(sampleRate, new Random());
    }

    /**
     * Construct a RandomEventSampler with the given sampleRate (from 0.0 to 1.0)
     * and Random instance.
     *
     * This constructor is primarily visible for testing, you should use
     * {@link RandomEventSampler#RandomEventSampler(double)}.
     *
     * @param sampleRate ratio of events to allow through to the server (from 0.0 to 1.0).
     * @param random Random instance to use for sampling, useful for testing.
     */
    public RandomEventSampler(double sampleRate, Random random) {
        this.sampleRate = (int) (sampleRate * RATE_MULTIPLIER);
        this.random = random;
    }

    /**
     * Handles event sampling logic.
     *
     * @param event Event to be checked against the sampling logic.
     * @return True if the event should be sent to the server, else False.
     */
    @Override
    public boolean shouldSendEvent(Event event) {
        return Math.abs(random.nextInt()) % RATE_MULTIPLIER < sampleRate;
    }
}
