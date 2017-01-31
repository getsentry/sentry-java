package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class RandomEventSamplerTest {
    private RandomEventSampler randomEventSampler = new RandomEventSampler(0.5);

    @Test
    public void testShouldSend() {
        UUID lowId = new UUID(0L, 25L);
        Event lowEvent = new EventBuilder(lowId).build();
        assertThat(randomEventSampler.shouldSendEvent(lowEvent), is(true));
    }

    public void testShouldNotSend() {
        UUID highId = new UUID(0L, 75L);
        Event highEvent = new EventBuilder(highId).build();
        assertThat(randomEventSampler.shouldSendEvent(highEvent), is(false));
    }

}
