package net.kencochrane.raven.event;

import net.kencochrane.raven.event.interfaces.SentryInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventBuilderTest {
    private EventBuilder eventBuilder;

    @BeforeMethod
    public void setUp() throws Exception {
        eventBuilder = new EventBuilder();
    }

    @Test
    public void testMandatoryValuesAutomaticallySet() throws Exception {
        Event event = eventBuilder.build();

        assertThat(event.getId(), is(notNullValue()));
        assertThat(event.getTimestamp(), is(notNullValue()));
        assertThat(event.getPlatform(), is(EventBuilder.DEFAULT_PLATFORM));
        //TODO: This test can fail if HostnameCache times out (happened once), mock InetAddress.getLocalHost().getCanonicalHostName() for instant reliable results)
        assertThat(event.getServerName(), is(InetAddress.getLocalHost().getCanonicalHostName()));
    }

    @Test
    public void testMandatoryValuesNotOverwritten() throws Exception {
        UUID uuid = UUID.randomUUID();
        Date timestamp = new Date();
        String platform = UUID.randomUUID().toString();
        String serverName = UUID.randomUUID().toString();

        Event event = new EventBuilder(uuid)
                .setTimestamp(timestamp)
                .setPlatform(platform)
                .setServerName(serverName)
                .build();

        assertThat(event.getId(), is(uuid));
        assertThat(event.getTimestamp(), is(timestamp));
        assertThat(event.getPlatform(), is(platform));
        assertThat(event.getServerName(), is(serverName));
    }

    @Test
    public void testEventParameters() throws Exception {
        String culprit = UUID.randomUUID().toString();
        String checksum = UUID.randomUUID().toString();
        Event.Level level = Event.Level.INFO;
        String logger = UUID.randomUUID().toString();
        String message = UUID.randomUUID().toString();

        Event event = eventBuilder.setCulprit(culprit)
                .setChecksum(checksum)
                .setLevel(level)
                .setLogger(logger)
                .setMessage(message)
                .build();

        assertThat(event.getCulprit(), is(culprit));
        assertThat(event.getChecksum(), is(checksum));
        assertThat(event.getLevel(), is(level));
        assertThat(event.getLogger(), is(logger));
        assertThat(event.getMessage(), is(message));
    }

    @Test
    public void testUseStackFrameAsCulprit() {
        StackTraceElement frame1 = new StackTraceElement("class", "method", "file", 1);
        StackTraceElement frame2 = new StackTraceElement("class", "method", "file", -1);
        StackTraceElement frame3 = new StackTraceElement("class", "method", null, 1);

        String culprit1 = new EventBuilder().setCulprit(frame1).build().getCulprit();
        String culprit2 = new EventBuilder().setCulprit(frame2).build().getCulprit();
        String culprit3 = new EventBuilder().setCulprit(frame3).build().getCulprit();

        assertThat(culprit1, is("class.method(file:1)"));
        assertThat(culprit2, is("class.method(file)"));
        assertThat(culprit3, is("class.method"));
    }

    @Test
    public void testChecksumGeneration() throws Exception {
        String cont = UUID.randomUUID().toString();
        Event noChecksumEvent = new EventBuilder().build();
        Event firstChecksumEvent = new EventBuilder().generateChecksum(cont).build();
        Event secondChecksumEvent = new EventBuilder().generateChecksum(cont).build();
        Event differentChecksumEvent = new EventBuilder().generateChecksum(UUID.randomUUID().toString()).build();

        assertThat(noChecksumEvent.getChecksum(), is(nullValue()));
        assertThat(firstChecksumEvent.getChecksum(), is(notNullValue()));
        assertThat(differentChecksumEvent.getChecksum(), is(notNullValue()));
        assertThat(firstChecksumEvent.getChecksum(), is(not(differentChecksumEvent.getChecksum())));
        assertThat(firstChecksumEvent.getChecksum(), is(secondChecksumEvent.getChecksum()));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testTagsAreImmutable() throws Exception {
        String tagKey = UUID.randomUUID().toString();
        String tagValue = UUID.randomUUID().toString();

        Map<String, String> tags = eventBuilder.addTag(tagKey, tagValue).build().getTags();

        assertThat(tags.size(), is(1));
        assertThat(tags.get(tagKey), is(tagValue));

        tags.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testExtrasAreImmutable() throws Exception {
        String extraKey = UUID.randomUUID().toString();
        Object extraValue = mock(Object.class);

        Map<String, Object> extra = eventBuilder.addExtra(extraKey, extraValue).build().getExtra();

        assertThat(extra.size(), is(1));
        assertThat(extra.get(extraKey), is(extraValue));

        extra.put(UUID.randomUUID().toString(), mock(Object.class));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSentryInterfacesAreImmutable() throws Exception {
        SentryInterface sentryInterface = mock(SentryInterface.class);
        when(sentryInterface.getInterfaceName()).thenReturn(UUID.randomUUID().toString());

        Map<String, SentryInterface> sentryInterfaces = eventBuilder
                .addSentryInterface(sentryInterface)
                .build()
                .getSentryInterfaces();

        assertThat(sentryInterfaces.size(), is(1));
        assertThat(sentryInterfaces.get(sentryInterface.getInterfaceName()), is(sentryInterface));

        sentryInterfaces.put(UUID.randomUUID().toString(), null);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testBuildCanBeCalledOnlyOnce() throws Exception {
        eventBuilder.build();
        eventBuilder.build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoUuidFails() throws Exception {
        new EventBuilder(null);
    }
}
