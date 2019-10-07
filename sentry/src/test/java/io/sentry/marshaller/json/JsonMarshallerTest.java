package io.sentry.marshaller.json;

import static java.util.Arrays.asList;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.BaseTest;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Sdk;
import junitparams.JUnitParamsRunner;
import junitparams.NamedParameters;
import junitparams.Parameters;
import io.sentry.event.Event;
import io.sentry.event.interfaces.SentryInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnitParamsRunner.class)
public class JsonMarshallerTest extends BaseTest {
    private JsonMarshaller jsonMarshaller = null;
    private Event mockEvent = null;

    @Before
    public void setUp() throws Exception {
        jsonMarshaller = new JsonMarshaller();
        // Do not compress by default during the tests
        jsonMarshaller.setCompression(false);

        mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        when(mockEvent.getTimestamp()).thenReturn(new Date(0));
        when(mockEvent.getLevel()).thenReturn(null);
        when(mockEvent.getSdk()).thenReturn(new Sdk(null, null, Collections.<String>emptySet()));
    }

    @Test
    public void testEventIdWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        UUID id = UUID.fromString("3b71fba5-413e-4022-ae98-5f0b80a155a5");
        when(mockEvent.getId()).thenReturn(id);

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testEventId.json")));
    }

    @Test
    public void testEventMessageWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        when(mockEvent.getMessage()).thenReturn("message");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testMessage.json")));
    }

    @Test
    public void testEventTimestampWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        // 2013-11-24T04:11:35.338 (UTC)
        Date mockTimestamp = new Date(1385266295338L);
        when(mockEvent.getTimestamp()).thenReturn(mockTimestamp);

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testTimestamp.json")));
    }

    @NamedParameters("levelProvider")
    public Object[][] levelProvider() {
        return new Object[][]{
                {Event.Level.DEBUG, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelDebug.json"},
                {Event.Level.INFO, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelInfo.json"},
                {Event.Level.WARNING, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelWarning.json"},
                {Event.Level.ERROR, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelError.json"},
                {Event.Level.FATAL, "/io/sentry/marshaller/json/jsonmarshallertest/testLevelFatal.json"},
        };
    }

    @Test
    @Parameters(named = "levelProvider")
    public void testEventLevelWrittenProperly(final Event.Level eventLevel, String levelFile) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getLevel()).thenReturn(eventLevel);

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource(levelFile)));
    }

    @Test
    public void testEventLoggerWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getLogger()).thenReturn("logger");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testLogger.json")));
    }

    @Test
    public void testEventPlaftormWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getPlatform()).thenReturn("platform");

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

        when(mockEvent.getSdk()).thenReturn(sdk);

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testSdk.json")));
    }

    @Test
    public void testEventCulpritWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getCulprit()).thenReturn("culprit");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testCulprit.json")));
    }

    @Test
    public void testEventTransactionWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getTransaction()).thenReturn("transaction");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testTransaction.json")));
    }

    @Test
    public void testEventTagsWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getTags()).thenReturn(Collections.singletonMap("tagName", "tagValue"));

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testTags.json")));
    }

    @Test
    public void testFingerPrintWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getFingerprint()).thenReturn(asList("fingerprint1", "fingerprint2"));

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testFingerprint.json")));
    }

    @Test
    public void testEventServerNameWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        when(mockEvent.getServerName()).thenReturn("serverName");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testServerName.json")));
    }

    @Test
    public void testEventReleaseWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        when(mockEvent.getRelease()).thenReturn("release");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testRelease.json")));
    }

    @Test
    public void testEventDistWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        when(mockEvent.getRelease()).thenReturn("release");
        when(mockEvent.getDist()).thenReturn("dist");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testDist.json")));
    }

    @Test
    public void testEventEnvironmentWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        when(mockEvent.getEnvironment()).thenReturn("environment");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testEnvironment.json")));
    }

    @Test
    public void testEventBreadcrumbsWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Breadcrumb breadcrumb1 = new BreadcrumbBuilder()
            .setTimestamp(new Date(1463169342123L))
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test1")
            .build();
        Breadcrumb breadcrumb2 = new BreadcrumbBuilder()
            .setTimestamp(new Date(1463169343111L))
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test2")
            .build();

        final List<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(breadcrumb1);
        breadcrumbs.add(breadcrumb2);

        when(mockEvent.getBreadcrumbs()).thenReturn(breadcrumbs);

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

        when(mockEvent.getContexts()).thenReturn(contexts);

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testContexts.json")));
    }

    @Test
    public void testEventChecksumWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getChecksum()).thenReturn("1234567890abcdef");

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testChecksum.json")));
    }

    @NamedParameters("extraProvider")
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

    @Test
    @Parameters(named = "extraProvider")
    public void testEventExtraWrittenProperly(final String extraKey, final Object extraValue, String extraFile) throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();
        when(mockEvent.getExtra()).thenReturn(Collections.singletonMap(extraKey, extraValue));

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource(extraFile)));
    }

    @Test
    public void testEventExtraWrittenProperly() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        when(mockEvent.getExtra()).thenReturn(Collections.<String, Object>singletonMap("key", new Object() {
            @Override
            public String toString() {
                return "test";
            }
        }));

        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        assertThat(jsonOutputStreamParser.value(), is(jsonResource("/io/sentry/marshaller/json/jsonmarshallertest/testExtraCustomValue.json")));
    }

    @Test
    public void testInterfaceBindingIsProperlyUsed() throws Exception {
        final JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        SentryInterface mockSentryInterface = mock(SentryInterface.class);
        when(mockEvent.getSentryInterfaces()).thenReturn(Collections.singletonMap("interfaceKey", mockSentryInterface));

        @SuppressWarnings("unchecked")
        InterfaceBinding<SentryInterface> mockInterfaceBinding = mock(InterfaceBinding.class);

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                JsonGenerator generator = invocation.getArgument(0);
                generator.writeNull();
                return null;
            }
        }).when(mockInterfaceBinding).writeInterface(any(JsonGenerator.class), eq(mockSentryInterface));

        jsonMarshaller.addInterfaceBinding(mockSentryInterface.getClass(), mockInterfaceBinding);
        jsonMarshaller.marshall(mockEvent, jsonOutputStreamParser.outputStream());

        verify(mockInterfaceBinding).writeInterface(any(JsonGenerator.class), eq(mockSentryInterface));

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
