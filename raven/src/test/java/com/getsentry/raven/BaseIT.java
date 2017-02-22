package com.getsentry.raven;

import com.getsentry.raven.connection.HttpConnection;
import com.getsentry.raven.unmarshaller.JsonUnmarshaller;
import com.getsentry.raven.unmarshaller.event.UnmarshalledEvent;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.junit.Rule;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BaseIT extends BaseTest {

    public static final String PROJECT1_ID = "1";
    public static final String PROJECT1_STORE_URL = "/api/" + PROJECT1_ID + "/store/";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8080));

    public String getDsn(String projectId) {
        return "http://public:private@localhost:" + wireMockRule.port() + "/" + projectId;
    }

    public URI getSentryServerUri() throws URISyntaxException {
        return new URI("http://localhost:" + wireMockRule.port() + "/");
    }

    public URL getProject1SentryStoreUrl() throws URISyntaxException {
        return HttpConnection.getSentryApiUrl(getSentryServerUri(), PROJECT1_ID);
    }

    public void verifyProject1PostRequestCount(int count) {
        verify(exactly(count), postRequestedFor(urlEqualTo(PROJECT1_STORE_URL)));
    }

    public void verifyStoredEventCount(int count) {
        assertThat(getStoredEvents().size(), is(count));
    }

    public List<UnmarshalledEvent> getStoredEvents() {
        JsonUnmarshaller unmarshaller = new JsonUnmarshaller();
        List<UnmarshalledEvent> events = new ArrayList<>();

        for (ServeEvent serveEvent : wireMockRule.getAllServeEvents()) {
            UnmarshalledEvent event = unmarshaller.unmarshal(new ByteArrayInputStream(serveEvent.getRequest().getBody()));
            events.add(event);
        }

        return events;
    }

}
