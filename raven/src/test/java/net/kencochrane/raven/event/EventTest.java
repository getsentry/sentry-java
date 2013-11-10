package net.kencochrane.raven.event;

import org.testng.annotations.Test;

public class EventTest {
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void ensureEventIdCantBeNull() throws Exception {
        final Event event = new Event(null);
    }
}
