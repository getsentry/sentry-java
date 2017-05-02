package io.sentry.time;

import io.sentry.BaseTest;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ClockTest extends BaseTest {

    @Test
    public void testFixedClock() {
        FixedClock fixedClock = new FixedClock(new Date(0));
        assertThat(new Date(0), equalTo(fixedClock.date()));

        fixedClock.setDate(new Date(1));
        assertThat(new Date(1), equalTo(fixedClock.date()));

        fixedClock.tick(1, TimeUnit.MILLISECONDS);
        assertThat(new Date(2), equalTo(fixedClock.date()));

        assertThat(new Date(2).getTime(), equalTo(fixedClock.millis()));
    }

}
