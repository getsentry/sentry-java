package net.kencochrane.raven.marshaller.json;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import net.kencochrane.raven.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getId();
            result = mockUuid;
            mockUuid.toString();
            result = "3b71fba5-413e-4022-ae98-5f0b80a155a5";
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testEventId.json")));
    }

    @Test
    public void testEventMessageWrittenProperly(@Injectable("message") final String mockMessage) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getMessage();
            result = mockMessage;
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testMessage.json")));
    }

    @Test
    public void testEventTimestampWrittenProperly(@Injectable final Date mockTimestamp) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getTimestamp();
            result = mockTimestamp;
            mockTimestamp.getTime();
            // 2013-11-24T04:11:35.338 (UTC)
            result = 1385266295338L;
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testTimestamp.json")));
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
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getLevel();
            result = eventLevel;
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource(levelFile)));
    }

    @Test
    public void testEventLoggerWrittenProperly(@Injectable("logger") final String mockLogger) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getLogger();
            result = mockLogger;
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testLogger.json")));
    }

    @Test
    public void testEventPlaftormWrittenProperly(@Injectable("platform") final String mockPlatform) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getPlatform();
            result = mockPlatform;
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testPlatform.json")));
    }

    @Test
    public void testEventCulpritWrittenProperly(@Injectable("culprit") final String mockCulprit) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getCulprit();
            result = mockCulprit;
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testCulprit.json")));
    }

    @Test
    public void testEventTagsWrittenProperly(@Injectable("tagName") final String mockTagName,
                                             @Injectable("tagValue") final String mockTagValue) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getTags();
            result = Collections.singletonMap(mockTagName, mockTagValue);
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testTags.json")));
    }

    @Test
    public void testEventServerNameWrittenProperly(@Injectable("serverName") final String mockServerName) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getServerName();
            result = mockServerName;
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testServerName.json")));
    }
}
