package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Charsets;
import mockit.Delegate;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Verifications;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static net.kencochrane.raven.marshaller.json.JsonTestTool.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonMarshallerTest {
    private final JsonMarshaller jsonMarshaller = new JsonMarshaller();
    @Injectable
    private Event mockEvent;

    @BeforeMethod
    public void setUp() throws Exception {
        // Do not compress by default during the tests
        jsonMarshaller.setCompression(false);
        new NonStrictExpectations() {{
            mockEvent.getId();
            result = UUID.fromString("00000000-0000-0000-0000-000000000000");
            mockEvent.getTimestamp();
            result = new Date(0);
        }};
    }

    @Test
    public void testEventIdWrittenProperly(@Injectable final UUID mockUuid) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getId();
            result = mockUuid;
            mockUuid.toString();
            result = "3b71fba5-413e-4022-ae98-5f0b80a155a5";
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testEventId.json")));
    }

    @Test
    public void testEventMessageWrittenProperly(@Injectable("message") final String mockMessage) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getMessage();
            result = mockMessage;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testMessage.json")));
    }

    @Test
    public void testEventTimestampWrittenProperly(@Injectable final Date mockTimestamp) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getTimestamp();
            result = mockTimestamp;
            mockTimestamp.getTime();
            // 2013-11-24T04:11:35.338 (UTC)
            result = 1385266295338L;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testTimestamp.json")));
    }

    @DataProvider(name = "levelProvider")
    public Object[][] levelProvider() {
        return new Object[][]{
                {Event.Level.DEBUG, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testLevelDebug.json"},
                {Event.Level.INFO, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testLevelInfo.json"},
                {Event.Level.WARNING, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testLevelWarning.json"},
                {Event.Level.ERROR, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testLevelError.json"},
                {Event.Level.FATAL, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testLevelFatal.json"},
        };
    }

    @Test(dataProvider = "levelProvider")
    public void testEventLevelWrittenProperly(final Event.Level eventLevel, String levelFile) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getLevel();
            result = eventLevel;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource(levelFile)));
    }

    @Test
    public void testEventLoggerWrittenProperly(@Injectable("logger") final String mockLogger) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getLogger();
            result = mockLogger;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testLogger.json")));
    }

    @Test
    public void testEventPlaftormWrittenProperly(@Injectable("platform") final String mockPlatform) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getPlatform();
            result = mockPlatform;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testPlatform.json")));
    }

    @Test
    public void testEventCulpritWrittenProperly(@Injectable("culprit") final String mockCulprit) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getCulprit();
            result = mockCulprit;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testCulprit.json")));
    }

    @Test
    public void testEventTagsWrittenProperly(@Injectable("tagName") final String mockTagName,
                                             @Injectable("tagValue") final String mockTagValue) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getTags();
            result = Collections.singletonMap(mockTagName, mockTagValue);
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testTags.json")));
    }

    @Test
    public void testEventServerNameWrittenProperly(@Injectable("serverName") final String mockServerName) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getServerName();
            result = mockServerName;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testServerName.json")));
    }

    @Test
    public void testEventChecksumWrittenProperly(@Injectable("1234567890abcdef") final String mockChecksum) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getChecksum();
            result = mockChecksum;
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testChecksum.json")));
    }

    @DataProvider(name = "extraProvider")
    public Object[][] extraProvider() {
        return new Object[][]{
                {"key", "string", "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraString.json"},
                {"key", 1, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraNumber.json"},
                {"key", true, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraBoolean.json"},
                {"key", null, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraNull.json"},
                {"key", new Object[]{"string", 1, null, true}, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraArray.json"},
                {"key", new Object[]{new Object[]{"string", 1, null, true}}, "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraRecursiveArray.json"},
                {"key", Arrays.asList(true, null, 1, "string"), "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraIterable.json"},
                {"key", Collections.singletonMap("key", "value"), "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraMap.json"},
                {"key", Collections.singletonMap(true, "value"), "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraObjectKeyMap.json"},
                {"key", Collections.singletonMap(null, "value"), "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraNullKeyMap.json"},
                {"key", Collections.singletonMap("key", Arrays.asList("string", 1, true, null)), "/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraRecursiveMap.json"}
        };
    }

    @Test(dataProvider = "extraProvider")
    public void testEventExtraWrittenProperly(final String extraKey, final Object extraValue, String extraFile) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getExtra();
            result = Collections.singletonMap(extraKey, extraValue);
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource(extraFile)));
    }

    @Test
    public void testEventExtraWrittenProperly(@Injectable("key") final String mockExtraKey,
                                              @Injectable final Object mockExtraValue) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getExtra();
            result = Collections.singletonMap(mockExtraKey, mockExtraValue);
            mockExtraValue.toString();
            result = "test";
        }};

        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testExtraCustomValue.json")));
    }

    @Test
    public void testInterfaceBindingIsProperlyUsed(
            @Injectable final SentryInterface mockSentryInterface,
            @Injectable final InterfaceBinding<SentryInterface> mockInterfaceBinding) throws Exception {
        final JsonOutputStreamTool outputStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getSentryInterfaces();
            result = Collections.singletonMap("interfaceKey", mockSentryInterface);
            mockInterfaceBinding.writeInterface((JsonGenerator) any, mockSentryInterface);
            result = new Delegate<Void>() {
                public void writeInterface(JsonGenerator generator, SentryInterface sentryInterface) throws IOException {
                    generator.writeNull();
                }
            };
        }};

        jsonMarshaller.addInterfaceBinding(mockSentryInterface.getClass(), mockInterfaceBinding);
        jsonMarshaller.marshall(mockEvent, outputStreamTool.outputStream());

        new Verifications() {{
            mockInterfaceBinding.writeInterface((JsonGenerator) any, mockSentryInterface);
        }};
        assertThat(outputStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testInterfaceBinding.json")));
    }

    @Test
    public void testCompressedDataIsWorking() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        jsonMarshaller.setCompression(true);

        jsonMarshaller.marshall(mockEvent, outputStream);

        assertThat(new String(outputStream.toByteArray(), Charsets.UTF_8.name()), is(""
                + "eJyFjcEOAiEMRP+lZ0zYk5Hv8L5psCKxsKSUjclm/"
                + "12islebucy087oBrZR1jjdwYP8MGEhUKwYClxuzAY"
                + "09UEylt6fL2Z7s1HW11n3UC9z5PM55CYFkuMKo90X"
                + "S8L5xkagHG0MFt+0GKslKMmdMx2N6qeB36x/kn7X9"
                + "MPsbwgxBSQ=="));
    }
}
