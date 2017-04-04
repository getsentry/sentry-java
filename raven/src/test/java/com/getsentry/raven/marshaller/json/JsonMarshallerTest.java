package com.getsentry.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.BreadcrumbBuilder;
import mockit.*;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.interfaces.SentryInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static com.getsentry.raven.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonMarshallerTest {
    @Tested
    private JsonMarshaller jsonMarshaller = null;
    @Injectable
    private Event mockEvent = null;

    @BeforeMethod
    public void setUp() throws Exception {
        jsonMarshaller = new JsonMarshaller();
        // Do not compress by default during the tests
        jsonMarshaller.setCompression(false);

        new NonStrictExpectations() {{
            mockEvent.getId();
            result = UUID.fromString("00000000-0000-0000-0000-000000000000");
            mockEvent.getTimestamp();
            result = new Date(0);
            mockEvent.getLevel();
            result = null;
        }};
    }

    @Test
    public void testEventIdWrittenProperly(@Injectable final UUID mockUuid) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getId();
            result = mockUuid;
            mockUuid.toString();
            result = "3b71fba5-413e-4022-ae98-5f0b80a155a5";
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testEventId.json")));
    }

    @Test
    public void testEventMessageWrittenProperly(@Injectable("message") final String mockMessage) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getMessage();
            result = mockMessage;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testMessage.json")));
    }

    @Test
    public void testEventTimestampWrittenProperly(@Injectable final Date mockTimestamp) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getTimestamp();
            result = mockTimestamp;
            mockTimestamp.getTime();
            // 2013-11-24T04:11:35.338 (UTC)
            result = 1385266295338L;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testTimestamp.json")));
    }

    @DataProvider(name = "levelProvider")
    public Object[][] levelProvider() {
        return new Object[][]{
                {Event.Level.DEBUG, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testLevelDebug.json"},
                {Event.Level.INFO, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testLevelInfo.json"},
                {Event.Level.WARNING, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testLevelWarning.json"},
                {Event.Level.ERROR, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testLevelError.json"},
                {Event.Level.FATAL, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testLevelFatal.json"},
        };
    }

    @Test(dataProvider = "levelProvider")
    public void testEventLevelWrittenProperly(final Event.Level eventLevel, String levelFile) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getLevel();
            result = eventLevel;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource(levelFile)));
    }

    @Test
    public void testEventLoggerWrittenProperly(@Injectable("logger") final String mockLogger) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getLogger();
            result = mockLogger;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testLogger.json")));
    }

    @Test
    public void testEventPlaftormWrittenProperly(@Injectable("platform") final String mockPlatform) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getPlatform();
            result = mockPlatform;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testPlatform.json")));
    }

    @Test
    public void testEventPlaftormWrittenProperly(@Injectable("sdkName") final String mockSdkName,
                                                 @Injectable("sdkVersion") final String mockSdkVersion) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getSdkName();
            result = mockSdkName;
            mockEvent.getSdkVersion();
            result = mockSdkVersion;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testSdk.json")));
    }

    @Test
    public void testEventCulpritWrittenProperly(@Injectable("culprit") final String mockCulprit) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getCulprit();
            result = mockCulprit;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testCulprit.json")));
    }

    @Test
    public void testEventTagsWrittenProperly(@Injectable("tagName") final String mockTagName,
                                             @Injectable("tagValue") final String mockTagValue) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getTags();
            result = Collections.singletonMap(mockTagName, mockTagValue);
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testTags.json")));
    }

    @Test
    public void testFingerPrintWrittenProperly(@Injectable("fingerprint1") final String mockFingerprint1,
                                             @Injectable("fingerprint2") final String mockFingerprint2) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getFingerprint();
            result = Arrays.asList(mockFingerprint1, mockFingerprint2);
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testFingerprint.json")));
    }


    @Test
    public void testEventServerNameWrittenProperly(@Injectable("serverName") final String mockServerName) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getServerName();
            result = mockServerName;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testServerName.json")));
    }

    @Test
    public void testEventReleaseWrittenProperly(@Injectable("release") final String mockRelease) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getRelease();
            result = mockRelease;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testRelease.json")));
    }

    @Test
    public void testEventEnvironmentWrittenProperly(@Injectable("environment") final String mockEnvironment) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getEnvironment();
            result = mockEnvironment;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testEnvironment.json")));
    }

    @Test
    public void testEventBreadcrumbsWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Breadcrumb breadcrumb1 = new BreadcrumbBuilder()
            .setTimestamp(new Date(1463169342000L))
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test1")
            .build();
        Breadcrumb breadcrumb2 = new BreadcrumbBuilder()
            .setTimestamp(new Date(1463169343000L))
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test2")
            .build();

        final List<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(breadcrumb1);
        breadcrumbs.add(breadcrumb2);

        new NonStrictExpectations() {{
            mockEvent.getBreadcrumbs();
            result = breadcrumbs;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testBreadcrumbs.json")));
    }

    @Test
    public void testEventContextsWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        final HashMap<String, Map<String, Object>> contexts = new HashMap<>();
        HashMap<String, Object> context1 = new HashMap<>();
        context1.put("context1key1", "context1value1");
        context1.put("context1key2", 12);
        context1.put("context1key3", 1.3);
        context1.put("context1key4", true);

        HashMap<String, Object> context2 = new HashMap<>();
        context2.put("context2key1", "context2value1");
        context2.put("context2key2", 22);
        context2.put("context2key3", 2.3);
        context2.put("context2key4", false);

        contexts.put("context1", context1);
        contexts.put("context2", context2);

        new NonStrictExpectations() {{
            mockEvent.getContexts();
            result = contexts;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testContexts.json")));
    }

    @Test
    public void testEventChecksumWrittenProperly(@Injectable("1234567890abcdef") final String mockChecksum) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getChecksum();
            result = mockChecksum;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testChecksum.json")));
    }

    @DataProvider(name = "extraProvider")
    public Object[][] extraProvider() {
        return new Object[][]{
                {"key", "string", "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraString.json"},
                {"key", 1, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraNumber.json"},
                {"key", true, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraBoolean.json"},
                {"key", null, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraNull.json"},
                {"key", new Object[]{"string", 1, null, true}, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraArray.json"},
                {"key", new Object[]{new Object[]{"string", 1, null, true}}, "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraRecursiveArray.json"},
                {"key", Arrays.asList(true, null, 1, "string"), "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraIterable.json"},
                {"key", Collections.singletonMap("key", "value"), "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraMap.json"},
                {"key", Collections.singletonMap(true, "value"), "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraObjectKeyMap.json"},
                {"key", Collections.singletonMap(null, "value"), "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraNullKeyMap.json"},
                {"key", Collections.singletonMap("key", Arrays.asList("string", 1, true, null)), "/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraRecursiveMap.json"}
        };
    }

    @Test(dataProvider = "extraProvider")
    public void testEventExtraWrittenProperly(final String extraKey, final Object extraValue, String extraFile) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getExtra();
            result = Collections.singletonMap(extraKey, extraValue);
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource(extraFile)));
    }

    @Test
    public void testEventExtraWrittenProperly(@Injectable("key") final String mockExtraKey,
                                              @Injectable final Object mockExtraValue) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getExtra();
            result = Collections.singletonMap(mockExtraKey, mockExtraValue);
            mockExtraValue.toString();
            result = "test";
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testExtraCustomValue.json")));
    }

    @Test
    public void testInterfaceBindingIsProperlyUsed(
            @Injectable final SentryInterface mockSentryInterface,
            @Injectable final InterfaceBinding<SentryInterface> mockInterfaceBinding) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getSentryInterfaces();
            result = Collections.singletonMap("interfaceKey", mockSentryInterface);
            mockInterfaceBinding.writeInterface((JsonGenerator) any, mockSentryInterface);
            result = new Delegate<Void>() {
                @SuppressWarnings("unused")
                public void writeInterface(JsonGenerator generator, SentryInterface sentryInterface) throws IOException {
                    generator.writeNull();
                }
            };
        }};

        jsonMarshaller.addInterfaceBinding(mockSentryInterface.getClass(), mockInterfaceBinding);
        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        new Verifications() {{
            mockInterfaceBinding.writeInterface((JsonGenerator) any, mockSentryInterface);
        }};
        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/jsonmarshallertest/testInterfaceBinding.json")));
    }

    @Test
    public void testCompressedDataIsWorking() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        jsonMarshaller.setCompression(true);

        jsonMarshaller.marshall(mockEvent, outputStream);

        assertThat(new String(outputStream.toByteArray(), "UTF-8"), is(""
                + "eJyFj8EKwyAMht8l5w7saczn2L1Imzlp1JLYMih994V1ym6T/+AX/z+JO"
                + "+CGqQxhAgvmz4EOIoo4j2DTStRBCVooLi6a7m9XczG96m6M/UgDpP2p2i"
                + "l7j1xpIVcemWPlcaWFQ6ko0wx2h+RiG7chS8jpxEPHOy/q0Zsg6+Pwa2Y"
                + "kdNIQ0xY4p6i/baVXYXfmxyeOs6zfXY43jPBZ0g=="
        ));
    }
}
