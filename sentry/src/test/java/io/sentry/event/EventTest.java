package io.sentry.event;

import io.sentry.BaseTest;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.*;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventTest extends BaseTest {
    @Test(expected = IllegalArgumentException.class)
    public void ensureEventIdCantBeNull() throws Exception {
        new Event(null);
    }

    @Test
    public void returnsCloneOfTimestamp() throws Exception {
        final Event event = new Event(UUID.randomUUID());

        Date timeStamp = new Date();
        event.setTimestamp(timeStamp);

        assertThat(event.getTimestamp(), is(equalTo(timeStamp)));
        assertThat(event.getTimestamp(), is(not(sameInstance(timeStamp))));
    }

    @Test
    public void serializedEventContainsSerializableExtras() throws Exception {
        final Event event = new Event(UUID.fromString("fb3fe928-69af-41a5-b76b-1db4c324caf6"));

        Object nonSerializableObject = mock(Object.class);
        when(nonSerializableObject.toString()).thenReturn("3c644639-9721-4e32-8cc8-a2b5b77f4424");

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
