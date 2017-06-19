package io.sentry.event;

import io.sentry.BaseTest;
import io.sentry.event.interfaces.DebugMetaInterface;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import io.sentry.event.interfaces.SentryInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.Date;
import java.util.UUID;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class EventBuilderTest extends BaseTest {
    @Injectable
    private InetAddress mockLocalHost = null;

    private static void resetHostnameCache() {
        setField(getHostnameCache(), "expirationTimestamp", 0l);
        setField(getHostnameCache(), "hostname", EventBuilder.DEFAULT_HOSTNAME);
    }

    private static Object getHostnameCache() {
        return getField(EventBuilder.class, "HOSTNAME_CACHE");
    }

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations(InetAddress.class) {{
            InetAddress.getLocalHost();
            result = mockLocalHost;
            mockLocalHost.getCanonicalHostName();
            result = "local";
        }};
    }

    @Test
    public void builtEventHasRandomlyGeneratedUuid(@Injectable final UUID mockUuid)
            throws Exception {
        new NonStrictExpectations(UUID.class) {{
            UUID.randomUUID();
            result = mockUuid;
        }};
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getId(), is(sameInstance(mockUuid)));
    }

    @Test
    public void builtEventWithCustomUuidHasProperUuid(@Injectable final UUID mockUuid)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder(mockUuid);

        final Event event = eventBuilder.build();

        assertThat(event.getId(), is(sameInstance(mockUuid)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
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
    public void builtEventWithMessageHasProperMessage(
            @Injectable("message") final String mockMessage)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withMessage(mockMessage);

        final Event event = eventBuilder.build();

        assertThat(event.getMessage(), is(sameInstance(mockMessage)));
    }

    @Test
    public void builtEventWithoutTimestampHasDefaultTimestamp(@Injectable final Date mockTimestamp)
            throws Exception {
        new NonStrictExpectations(Date.class) {{
            new Date();
            result = mockTimestamp;
            mockTimestamp.clone();
            result = mockTimestamp;
        }};
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getTimestamp(), is(sameInstance(mockTimestamp)));
    }

    @Test
    public void builtEventWithTimestampHasProperTimestamp(@Injectable final Date mockTimestamp)
            throws Exception {
        new NonStrictExpectations() {{
            mockTimestamp.clone();
            result = mockTimestamp;
        }};
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withTimestamp(mockTimestamp);

        final Event event = eventBuilder.build();

        assertThat(event.getTimestamp(), is(sameInstance(mockTimestamp)));
    }

    @Test
    public void builtEventWithoutLevelHasNullLevel() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getLevel(), is(nullValue()));
    }

    @Test
    public void builtEventWithLevelHasProperLevel(@Injectable final Event.Level mockLevel)
            throws Exception {
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
    public void builtEventWithLoggerHasProperLogger(@Injectable("logger") final String mockLogger)
            throws Exception {
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
    public void builtEventWithPlatformHasProperPlatform(@Injectable("platform") final String mockPlatform)
            throws Exception {
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
    public void builtEventWithCulpritHasProperCulprit(@Injectable("culprit") final String mockCulprit)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withCulprit(mockCulprit);

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(sameInstance(mockCulprit)));
    }

    @DataProvider
    public Object[][] stackFrameProvider() {
        return new Object[][]{
                {new StackTraceElement("class", "method", "file", 12), "class.method(file:12)"},
                {new StackTraceElement("class", "method", "file", -1), "class.method(file)"},
                {new StackTraceElement("class", "method", null, 12), "class.method"},
                {new StackTraceElement("class", "method", null, -1), "class.method"}
        };
    }

    @Test(dataProvider = "stackFrameProvider")
    public void builtEventWithStackFrameAsCulpritHasProperCulprit(StackTraceElement mockStackFrame,
                                                                  String expectedCulprit)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withCulprit(mockStackFrame);

        final Event event = eventBuilder.build();

        assertThat(event.getCulprit(), is(expectedCulprit));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
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
    public void builtEventWithTagsHasProperTags(@Injectable("tagKey") final String mockTagKey,
                                                @Injectable("tagValue") final String mockTagValue)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withTag(mockTagKey, mockTagValue);

        final Event event = eventBuilder.build();

        assertThat(event.getTags(), hasEntry(mockTagKey, mockTagValue));
        assertThat(event.getTags().entrySet(), hasSize(1));
    }

    @Test
    public void builtEventWithNoServerNameUsesDefaultIfSearchTimesOut()
            throws Exception {
        resetHostnameCache();
        new NonStrictExpectations(InetAddress.class) {{
            InetAddress.getLocalHost();
            result = mockLocalHost;
            mockLocalHost.getCanonicalHostName();
            result = new RuntimeException("For all intents and purposes, an exception is the same as a timeout");
        }};
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();
        synchronized (this) {
            this.notify();
        }

        assertThat(event.getServerName(), is(EventBuilder.DEFAULT_HOSTNAME));
    }

    @Test
    public void builtEventWithNoServerNameUsesLocalHost(@Injectable("serverName") final String mockServerName)
            throws Exception {
        resetHostnameCache();
        new NonStrictExpectations(InetAddress.class) {{
            InetAddress.getLocalHost();
            result = mockLocalHost;
            mockLocalHost.getCanonicalHostName();
            result = mockServerName;
        }};
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getServerName(), is(mockServerName));
    }

    @Test
    public void builtEventWithServerNameUsesProvidedServerName(@Injectable("serverName") final String mockServerName)
            throws Exception {
        resetHostnameCache();
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withServerName(mockServerName);

        final Event event = eventBuilder.build();

        assertThat(event.getServerName(), is(mockServerName));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
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
    public void builtEventWithExtrasHasProperExtras(@Injectable("extraKey") final String mockExtraKey,
                                                    @Injectable("extraValue") final String mockExtraValue)
            throws Exception {
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
    public void builtEventWithChecksumHasProperChecksum(
            @Injectable("checksum") final String mockChecksum)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withChecksum(mockChecksum);

        final Event event = eventBuilder.build();

        assertThat(event.getChecksum(), is(sameInstance(mockChecksum)));
    }

    @DataProvider
    public Object[][] checksumProvider() {
        return new Object[][]{
                {"", "0"},
                {"test", "D87F7E0C"},
                {"otherTest", "77B2E45B"}
        };
    }

    @Test(dataProvider = "checksumProvider")
    public void builtEventWithGeneratedChecksumHasCRC32Checksum(String string, String expectedChecksum)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withChecksumFor(string);

        final Event event = eventBuilder.build();

        assertThat(event.getChecksum(), is(expectedChecksum));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void builtEventHasImmutableSentryInterfaces(@Injectable final SentryInterface mockSentryInterface)
            throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();
        final Event event = eventBuilder.build();

        event.getSentryInterfaces().put("interfaceName", mockSentryInterface);
    }

    @Test
    public void builtEventWithoutSentryInterfacesHasEmptySentryInterfaces() throws Exception {
        final EventBuilder eventBuilder = new EventBuilder();

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces().entrySet(), is(empty()));
    }

    @Test
    public void builtEventWithSentryInterfacesHasProperSentryInterfaces(
            @Injectable("sentryInterfaceName") final String mockSentryInterfaceName,
            @Injectable final SentryInterface mockSentryInterface)
            throws Exception {
        new NonStrictExpectations() {{
            mockSentryInterface.getInterfaceName();
            result = mockSentryInterfaceName;
        }};
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry(mockSentryInterfaceName, mockSentryInterface));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }

    @Test
    public void builtEventReplacesSentryInterfacesWithSameNameByDefault(
            @Injectable("sentryInterfaceName") final String mockSentryInterfaceName,
            @Injectable final SentryInterface mockSentryInterface,
            @Injectable final SentryInterface mockSentryInterface2)
            throws Exception {
        new NonStrictExpectations() {{
            mockSentryInterface.getInterfaceName();
            result = mockSentryInterfaceName;
            mockSentryInterface2.getInterfaceName();
            result = mockSentryInterfaceName;
        }};
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);
        eventBuilder.withSentryInterface(mockSentryInterface2);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry(mockSentryInterfaceName, mockSentryInterface2));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }


    @Test
    public void builtEventReplacesSentryInterfacesWithSameNameIfReplacementEnabled(
            @Injectable("sentryInterfaceName") final String mockSentryInterfaceName,
            @Injectable final SentryInterface mockSentryInterface,
            @Injectable final SentryInterface mockSentryInterface2)
            throws Exception {
        new NonStrictExpectations() {{
            mockSentryInterface.getInterfaceName();
            result = mockSentryInterfaceName;
            mockSentryInterface2.getInterfaceName();
            result = mockSentryInterfaceName;
        }};
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);
        eventBuilder.withSentryInterface(mockSentryInterface2, true);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry(mockSentryInterfaceName, mockSentryInterface2));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }

    @Test
    public void builtEventKeepsSentryInterfacesWithSameNameIfReplacementDisabled(
            @Injectable("sentryInterfaceName") final String mockSentryInterfaceName,
            @Injectable final SentryInterface mockSentryInterface,
            @Injectable final SentryInterface mockSentryInterface2)
            throws Exception {
        new NonStrictExpectations() {{
            mockSentryInterface.getInterfaceName();
            result = mockSentryInterfaceName;
            mockSentryInterface2.getInterfaceName();
            result = mockSentryInterfaceName;
        }};
        final EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.withSentryInterface(mockSentryInterface);
        eventBuilder.withSentryInterface(mockSentryInterface2, false);

        final Event event = eventBuilder.build();

        assertThat(event.getSentryInterfaces(), hasEntry(mockSentryInterfaceName, mockSentryInterface));
        assertThat(event.getSentryInterfaces().entrySet(), hasSize(1));
    }

    @Test(expectedExceptions = IllegalStateException.class)
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
