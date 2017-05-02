package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class RandomEventSamplerTest extends BaseTest {
    private Event event = new EventBuilder().build();

    private void testRandomEventSampler(double sampleRate, double fakeRandom, boolean expected) {
        RandomEventSampler randomEventSampler = new RandomEventSampler(sampleRate, new FakeRandom(fakeRandom));
        assertThat(randomEventSampler.shouldSendEvent(event), is(expected));
    }

    @Test
    public void testShouldSend() {
        testRandomEventSampler(0.8, 0.75, true);
        testRandomEventSampler(1.0, 0.99, true);
        testRandomEventSampler(0.1, 0.75, false);
        testRandomEventSampler(0.0, 1.0, false);
    }

    private class FakeRandom extends Random {
        private double fakeRandom;

        FakeRandom(double FakeRandom) {
            fakeRandom = FakeRandom;
        }

        @Override
        public double nextDouble() {
            return fakeRandom;
        }
    }
}
