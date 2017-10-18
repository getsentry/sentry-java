package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.BaseTest;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Sdk;
import mockit.*;
import io.sentry.event.Event;
import io.sentry.event.interfaces.SentryInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonMarshallerTest extends BaseTest {
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testEventId.json")));
    }

    @Test
    public void testEventMessageWrittenProperly(@Injectable("message") final String mockMessage) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getMessage();
            result = mockMessage;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testMessage.json")));
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testTimestamp.json")));
    }

    @DataProvider(name = "levelProvider")
    public Object[][] levelProvider() {
        return new Object[][]{
                {Event.Level.DEBUG, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelDebug.json"},
                {Event.Level.INFO, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelInfo.json"},
                {Event.Level.WARNING, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelWarning.json"},
                {Event.Level.ERROR, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelError.json"},
                {Event.Level.FATAL, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelFatal.json"},
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testLogger.json")));
    }

    @Test
    public void testEventPlaftormWrittenProperly(@Injectable("platform") final String mockPlatform) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getPlatform();
            result = mockPlatform;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testPlatform.json")));
    }

    @Test
    public void testEventSdkWrittenProperly() throws Exception {
        HashSet<String> integrations = new HashSet<>();
        integrations.add("integration1");
        integrations.add("integration2");
        final Sdk sdk = new Sdk("sdkName", "sdkVersion", integrations);
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getSdk();
            result = sdk;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testSdk.json")));
    }

    @Test
    public void testEventCulpritWrittenProperly(@Injectable("culprit") final String mockCulprit) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getCulprit();
            result = mockCulprit;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testCulprit.json")));
    }

    @Test
    public void testEventTransactionWrittenProperly(@Injectable("transaction") final String mockTransaction) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getTransaction();
            result = mockTransaction;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testTransaction.json")));
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testTags.json")));
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testFingerprint.json")));
    }


    @Test
    public void testEventServerNameWrittenProperly(@Injectable("serverName") final String mockServerName) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getServerName();
            result = mockServerName;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testServerName.json")));
    }

    @Test
    public void testEventReleaseWrittenProperly(@Injectable("release") final String mockRelease) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getRelease();
            result = mockRelease;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testRelease.json")));
    }

    @Test
    public void testEventDistWrittenProperly(@Injectable("release") final String mockRelease,
                                             @Injectable("dist") final String mockDist) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getRelease();
            result = mockRelease;
            mockEvent.getDist();
            result = mockDist;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testDist.json")));
    }

    @Test
    public void testEventEnvironmentWrittenProperly(@Injectable("environment") final String mockEnvironment) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getEnvironment();
            result = mockEnvironment;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testEnvironment.json")));
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testBreadcrumbs.json")));
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testContexts.json")));
    }

    @Test
    public void testEventChecksumWrittenProperly(@Injectable("1234567890abcdef") final String mockChecksum) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getChecksum();
            result = mockChecksum;
        }};

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testChecksum.json")));
    }

    @DataProvider(name = "extraProvider")
    public Object[][] extraProvider() {
        return new Object[][]{
                {"key", "string", "/io/sentry/marshaller/json/jsonmarshallertest/testExtraString.json"},
                {"key", 1, "/io/sentry/marshaller/json/jsonmarshallertest/testExtraNumber.json"},
                {"key", true, "/io/sentry/marshaller/json/jsonmarshallertest/testExtraBoolean.json"},
                {"key", null, "/io/sentry/marshaller/json/jsonmarshallertest/testExtraNull.json"},
                {"key", new Object[]{"string", 1, null, true}, "/io/sentry/marshaller/json/jsonmarshallertest/testExtraArray.json"},
                {"key", new Object[]{new Object[]{"string", 1, null, true}}, "/io/sentry/marshaller/json/jsonmarshallertest/testExtraRecursiveArray.json"},
                {"key", Arrays.asList(true, null, 1, "string"), "/io/sentry/marshaller/json/jsonmarshallertest/testExtraIterable.json"},
                {"key", Collections.singletonMap("key", "value"), "/io/sentry/marshaller/json/jsonmarshallertest/testExtraMap.json"},
                {"key", Collections.singletonMap(true, "value"), "/io/sentry/marshaller/json/jsonmarshallertest/testExtraObjectKeyMap.json"},
                {"key", Collections.singletonMap(null, "value"), "/io/sentry/marshaller/json/jsonmarshallertest/testExtraNullKeyMap.json"},
                {"key", Collections.singletonMap("key", Arrays.asList("string", 1, true, null)), "/io/sentry/marshaller/json/jsonmarshallertest/testExtraRecursiveMap.json"}
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

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testExtraCustomValue.json")));
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
        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testInterfaceBinding.json")));
    }

    @Test
    public void testCompressedDataIsWorking() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        jsonMarshaller.setCompression(true);

        jsonMarshaller.marshall(mockEvent, outputStream);

        JsonNode json = deserialize(decompress(outputStream.toByteArray()));
        assertThat(json, is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testCompression.json")));
    }

    private String decompress(byte[] compressedData) throws Exception {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData));
        Scanner s = new Scanner(gzipInputStream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private JsonNode deserialize(String jsonString) throws Exception {
        return new ObjectMapper().readTree(jsonString);
    }
}
