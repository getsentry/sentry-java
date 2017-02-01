package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class RandomEventSamplerTest {
    private Event event = new EventBuilder().build();
    private Random seededRandom = new Random();

    @BeforeMethod
    public void setup() {
        // set our Random to a known seed such that nextInt % 100 == -25
        seededRandom.setSeed(1);
    }

    @Test
    public void testShouldSend() {
        RandomEventSampler randomEventSampler = new RandomEventSampler(0.5, seededRandom);
        assertThat(randomEventSampler.shouldSendEvent(event), is(true));
    }

    @Test
    public void testShouldNotSend() {
        RandomEventSampler randomEventSampler = new RandomEventSampler(0.1, seededRandom);
        assertThat(randomEventSampler.shouldSendEvent(event), is(false));
    }

}
