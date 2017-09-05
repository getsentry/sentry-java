package io.sentry.event;

import io.sentry.BaseTest;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class EventTest extends BaseTest {
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void ensureEventIdCantBeNull() throws Exception {
        new Event(null);
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

    @Test
    public void serializedEventContainsSerializableExtras(@Injectable final Object nonSerializableObject)
            throws Exception {
        final Event event = new Event(UUID.fromString("fb3fe928-69af-41a5-b76b-1db4c324caf6"));
        new NonStrictExpectations() {{
            nonSerializableObject.toString();
            result = "3c644639-9721-4e32-8cc8-a2b5b77f4424";
        }};
        event.getExtra().put("SerializableEntry", 38295L);
        event.getExtra().put("NonSerializableEntry", nonSerializableObject);
        event.getExtra().put("NullEntry", null);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(event);
        ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
        Event receivedEvent = (Event) is.readObject();

        assertThat(receivedEvent.getId(), equalTo(event.getId()));
        assertThat(receivedEvent.getExtra().get("SerializableEntry"), Matchers.<Object>equalTo(38295L));
        assertThat(receivedEvent.getExtra().get("NonSerializableEntry"),
                Matchers.<Object>equalTo("3c644639-9721-4e32-8cc8-a2b5b77f4424"));
        assertThat(receivedEvent.getExtra().get("NullEntry"),
            Matchers.<Object>equalTo(null));
    }
}
