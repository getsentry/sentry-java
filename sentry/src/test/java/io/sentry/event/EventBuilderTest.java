package io.sentry.event;

import io.sentry.BaseTest;
import io.sentry.event.interfaces.DebugMetaInterface;
import junitparams.JUnitParamsRunner;
import junitparams.NamedParameters;
import junitparams.Parameters;
import io.sentry.event.interfaces.SentryInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnitParamsRunner.class)
public class EventBuilderTest extends BaseTest {
    @Before
    public void setUp() throws Exception {
        EventBuilder.HOSTNAME_CACHE.reset(System.currentTimeMillis());
    }

    @Test
    public void builtEventHasRandomlyGeneratedUuid() throws Exception {

        Event ev1 = new EventBuilder().build();
        Event ev2 = new EventBuilder().build();

        assertThat(ev1.getId(), not(is(ev2.getId())));
    }

    @Test
    public void builtEventWithCustomUuidHasProperUuid() throws Exception {
        UUID id = UUID.randomUUID();
        final EventBuilder eventBuilder = new EventBuilder(id);

        final Event event = eventBuilder.build();

        assertThat(event.getId(), is(sameInstance(id)));
    }

    @Test(expected= IllegalArgumentException.class)
    public void builtEventWithCustomNullUuidFails() throws Exception {
        new EventBuilder(null);
    }

    @Test
    public void builtEventWithoutMessageHasNullMessage() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getMessage(), is(nullValue()));
    }

    @Test
    public void builtEventWithMessageHasProperMessage() throws Exception {
        String mockMessage = "teh massage";
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withMessage(mockMessage);

        final Event event = eventBuilder.build();

        assertThat(event.getMessage(), is(sameInstance(mockMessage)));
    }

    @Test
    public void builtEventWithoutTimestampHasDefaultTimestamp() throws Exception {
        long before = System.currentTimeMillis();

        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        long after = System.currentTimeMillis();

        long eventTimestamp = event.getTimestamp().getTime();

        assertThat(eventTimestamp, is(greaterThanOrEqualTo(before)));
        assertThat(eventTimestamp, is(lessThanOrEqualTo(after)));
    }

    @Test
    public void builtEventWithTimestampHasProperTimestamp() throws Exception {
        Date mockTimestamp = new Date(new Random().nextLong());
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withTimestamp(mockTimestamp);

        final Event event = eventBuilder.build();

        assertThat(event.getTimestamp(), is(not(sameInstance(mockTimestamp))));
        assertThat(event.getTimestamp(), is(mockTimestamp));
    }

    @Test
    public void builtEventWithoutLevelHasNullLevel() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getLevel(), is(nullValue()));
    }

    @Test
    public void builtEventWithLevelHasProperLevel() throws Exception {
        Event.Level mockLevel = Event.Level.INFO;
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withLevel(mockLevel);

        final Event event = eventBuilder.build();

        assertThat(event.getLevel(), is(sameInstance(mockLevel)));
    }

    @Test
    public void builtEventWithoutLoggerHasNullLogger() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getLogger(), is(nullValue()));
    }

    @Test
    public void builtEventWithLoggerHasProperLogger() throws Exception {
        String mockLogger = "mockLogger";
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withLogger(mockLogger);

        final Event event = eventBuilder.build();

        assertThat(event.getLogger(), is(sameInstance(mockLogger)));
    }

    @Test
    public void builtEventWithoutPlatformHasDefaultPlatform() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getPlatform(), is(EventBuilder.DEFAULT_PLATFORM));
    }

    @Test
    public void builtEventWithPlatformHasProperPlatform() throws Exception {
        String mockPlatform = "mockPlatform";
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withPlatform(mockPlatform);

        final Event event = eventBuilder.build();

        assertThat(event.getPlatform(), is(sameInstance(mockPlatform)));
    }

    @Test
    public void builtEventWithoutCulpritHasNullCulprit() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(nullValue()));
    }

    @Test
    public void builtEventWithCulpritHasProperCulprit() throws Exception {
        String mockCulprit = "mockCulprit";
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withCulprit(mockCulprit);

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(sameInstance(mockCulprit)));
    }

    @NamedParameters("stackFrames")
    public Object[][] stackFrameProvider() {
        return new Object[][]{
                {new StackTraceElement("class", "method", "file", 12), "class.method(file:12)"},
                {new StackTraceElement("class", "method", "file", -1), "class.method(file)"},
                {new StackTraceElement("class", "method", null, 12), "class.method"},
                {new StackTraceElement("class", "method", null, -1), "class.method"}
        };
    }

    @Test
    @Parameters(named = "stackFrames")
    public void builtEventWithStackFrameAsCulpritHasProperCulprit(StackTraceElement mockStackFrame,
                                                                  String expectedCulprit)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withCulprit(mockStackFrame);

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(expectedCulprit));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void builtEventHasImmutableTags() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        final Event event = eventBuilder.build();

        event.getTags().put("tagKey", "tagValue");
    }

    @Test
    public void builtEventWithoutTagsHasEmptyTags() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getTags().entrySet(), is(empty()));
    }

    @Test
    public void builtEventWithTagsHasProperTags() throws Exception {
        String mockTagKey = "tagKey";
        String mockTagValue = "tagValue";
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withTag(mockTagKey, mockTagValue);

        final Event event = eventBuilder.build();

        assertThat(event.getTags(), hasEntry(mockTagKey, mockTagValue));
        assertThat(event.getTags().entrySet(), hasSize(1));
    }


    @Test
    public void builtEventWithNoServerNameUsesLocalHost() throws Exception {
        EventBuilder.HOSTNAME_CACHE.reset(System.currentTimeMillis() + 1_000_000_000);
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getServerName(), is(EventBuilder.DEFAULT_HOSTNAME));
    }

    @Test
    public void builtEventWithServerNameUsesProvidedServerName() throws Exception {
        String mockServerName = "mockServer.name";

        // this is to make the hostname cache think it has to resolve the hostname. But because we provide the
        // server name on our own, this should actually never happen.
        EventBuilder.HOSTNAME_CACHE.reset(System.currentTimeMillis() - 100);

        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withServerName(mockServerName);

        final Event event = eventBuilder.build();

        assertThat(event.getServerName(), is(mockServerName));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void builtEventHasImmutableExtras() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        final Event event = eventBuilder.build();

        event.getExtra().put("extraKey", "extraKey");
    }

    @Test
    public void builtEventWithoutExtrasHasEmptyExtras() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getExtra().entrySet(), is(empty()));
    }

    @Test
    public void builtEventWithExtrasHasProperExtras() throws Exception {
        String mockExtraKey = "key";
        String mockExtraValue = "value";
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withExtra(mockExtraKey, mockExtraValue);

        final Event event = eventBuilder.build();

        assertThat(event.getExtra(), hasEntry(mockExtraKey, (Object) mockExtraValue));
        assertThat(event.getExtra().entrySet(), hasSize(1));
    }

    @Test
    public void builtEventWithoutCheckHasNullChecksum() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getChecksum(), is(nullValue()));
    }

    @Test
    public void builtEventWithChecksumHasProperChecksum() throws Exception {
        String mockChecksum = "checksum";
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withChecksum(mockChecksum);

        final Event event = eventBuilder.build();

        assertThat(event.getChecksum(), is(sameInstance(mockChecksum)));
    }

    @NamedParameters("checksums")
    public Object[][] checksumProvider() {
        return new Object[][]{
                {"", "0"},
                {"test", "D87F7E0C"},
                {"otherTest", "77B2E45B"}
        };
    }

    @Test
    @Parameters(named = "checksums")
    public void builtEventWithGeneratedChecksumHasCRC32Checksum(String string, String expectedChecksum)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withChecksumFor(string);

        final Event event = eventBuilder.build();

        assertThat(event.getChecksum(), is(expectedChecksum));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void builtEventHasImmutableSentryInterfaces() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        final Event event = eventBuilder.build();

        event.getSentryInterfaces().put("interfaceName", mock(SentryInterface.class));
    }

    @Test
    public void builtEventWithoutSentryInterfacesHasEmptySentryInterfaces() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces().entrySet(), is(empty()));
    }

    @Test
    public void builtEventWithSentryInterfacesHasProperSentryInterfaces() throws Exception {

        SentryInterface mockSentryInterface = mock(SentryInterface.class);
        when(mockSentryInterface.getInterfaceName()).thenReturn("sentryInterfaceName");

        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry("sentryInterfaceName", mockSentryInterface));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }

    @Test
    public void builtEventReplacesSentryInterfacesWithSameNameByDefault() throws Exception {
        String mockSentryInterfaceName = "sentryInterfaceName";
        SentryInterface mockSentryInterface = mock(SentryInterface.class);
        SentryInterface mockSentryInterface2 = mock(SentryInterface.class);

        when(mockSentryInterface.getInterfaceName()).thenReturn(mockSentryInterfaceName);
        when(mockSentryInterface2.getInterfaceName()).thenReturn(mockSentryInterfaceName);

        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);
        eventBuilder.withSentryInterface(mockSentryInterface2);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry(mockSentryInterfaceName, mockSentryInterface2));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }

        @Test
    public void builtEventReplacesSentryInterfacesWithSameNameIfReplacementEnabled() throws Exception {
        String mockSentryInterfaceName = "sentryInterfaceName";
        SentryInterface mockSentryInterface = mock(SentryInterface.class);
        SentryInterface mockSentryInterface2 = mock(SentryInterface.class);

        when(mockSentryInterface.getInterfaceName()).thenReturn(mockSentryInterfaceName);
        when(mockSentryInterface2.getInterfaceName()).thenReturn(mockSentryInterfaceName);

        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);
        eventBuilder.withSentryInterface(mockSentryInterface2, true);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry(mockSentryInterfaceName, mockSentryInterface2));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }

    @Test
    public void builtEventKeepsSentryInterfacesWithSameNameIfReplacementDisabled() throws Exception {
        String mockSentryInterfaceName = "sentryInterfaceName";
        SentryInterface mockSentryInterface = mock(SentryInterface.class);
        SentryInterface mockSentryInterface2 = mock(SentryInterface.class);

        when(mockSentryInterface.getInterfaceName()).thenReturn(mockSentryInterfaceName);
        when(mockSentryInterface2.getInterfaceName()).thenReturn(mockSentryInterfaceName);

        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);
        eventBuilder.withSentryInterface(mockSentryInterface2, false);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry(mockSentryInterfaceName, mockSentryInterface));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }

    @Test(expected = IllegalStateException.class)
    public void buildingTheEventTwiceFails() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.build();
        eventBuilder.build();
    }

    @Test
    public void builtEventWithDebugMeta() {
        final EventBuilder eventBuilder = new EventBuilder();
        DebugMetaInterface.DebugImage image1 = new DebugMetaInterface.DebugImage("abcd-efgh");
        DebugMetaInterface.DebugImage image2 = new DebugMetaInterface.DebugImage("ijkl-mnop");
        final DebugMetaInterface debugInterface = new DebugMetaInterface();
        debugInterface.addDebugImage(image1);
        debugInterface.addDebugImage(image2);

        eventBuilder.withSentryInterface(debugInterface);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasKey(DebugMetaInterface.DEBUG_META_INTERFACE));
    }
}
