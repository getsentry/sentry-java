package net.kencochrane.raven.event;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class EventTest {
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void ensureEventIdCantBeNull() throws Exception {
        final Event event = new Event(null);
    }

    @Test
    public void returnsCloneOfTimestamp(@Injectable final Date mockTimestamp,
                                        @Injectable final Date mockCloneTimestamp,
                                        @Injectable final UUID mockUuid)
            throws Exception {
        new NonStrictExpectations() {{
            mockTimestamp.clone();
            result = mockCloneTimestamp;
        }};
        final Event event = new Event(mockUuid);

        event.setTimestamp(mockTimestamp);

        assertThat(event.getTimestamp(), is(sameInstance(mockCloneTimestamp)));
    }
}
