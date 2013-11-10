package net.kencochrane.raven.event;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EventBuilderTest2 {
    @Test
    public void buildedEventHasRandomlyGeneratedUuid(@Injectable final UUID mockUuid) throws Exception {
        new NonStrictExpectations(UUID.class) {{
            UUID.randomUUID();
            result = mockUuid;
        }};
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getId(), is(mockUuid));
    }
}
