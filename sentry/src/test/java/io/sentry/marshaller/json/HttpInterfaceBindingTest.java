package io.sentry.marshaller.json;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.fasterxml.jackson.databind.JsonNode;
import io.sentry.BaseTest;
import io.sentry.event.interfaces.HttpInterface;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpInterfaceBindingTest extends BaseTest {
    private HttpInterfaceBinding interfaceBinding = null;
    private HttpInterface mockMessageInterface = null;
    private JsonGeneratorParser jsonGeneratorParser;

    @Before
    public void setup() throws Exception {
        mockMessageInterface = mock(HttpInterface.class);
        interfaceBinding = new HttpInterfaceBinding();
        jsonGeneratorParser = newJsonGenerator();

        final Map<String, Collection<String>> headers = new HashMap<>();
        headers.put("Header1", singletonList("Value1"));
        headers.put("Header2", asList("Value1", "Value2"));
        final HashMap<String, String> cookies = new HashMap<>();
        cookies.put("Cookie1", "Value1");

        when(mockMessageInterface.getHeaders()).thenReturn(headers);
        when(mockMessageInterface.getRequestUrl()).thenReturn("http://host/url");
        when(mockMessageInterface.getMethod()).thenReturn("GET");
        when(mockMessageInterface.getQueryString()).thenReturn("query");
        when(mockMessageInterface.getCookies()).thenReturn(cookies);
        when(mockMessageInterface.getRemoteAddr()).thenReturn("1.2.3.4");
        when(mockMessageInterface.getServerName()).thenReturn("server-name");
        when(mockMessageInterface.getServerPort()).thenReturn(1234);
        when(mockMessageInterface.getLocalPort()).thenReturn(5678);
        when(mockMessageInterface.getProtocol()).thenReturn("HTTP");
        when(mockMessageInterface.getLocalAddr()).thenReturn("5.6.7.8");
        when(mockMessageInterface.getLocalName()).thenReturn("local-name");
        when(mockMessageInterface.getBody()).thenReturn("body");
    }

    @Test
    public void testHeaders() throws Exception {
        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockMessageInterface);
        JsonNode value = jsonGeneratorParser.value();
        assertThat(value, is(jsonResource("/io/sentry/marshaller/json/Http1.json")));
    }

    @Test
    public void testBodyWithParameters() throws Exception {
        final HashMap<String, Collection<String>> parameters = new HashMap<>();
        parameters.put("Parameter1", singletonList("Value1"));
        parameters.put("Parameter2", asList("Value2", "Value3"));

        when(mockMessageInterface.getParameters()).thenReturn(parameters);

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockMessageInterface);

        JsonNode value = jsonGeneratorParser.value();
        assertThat(value, is(jsonResource("/io/sentry/marshaller/json/Http2.json")));
    }
}
