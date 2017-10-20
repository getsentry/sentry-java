package io.sentry;

import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import io.sentry.connection.HttpConnection;
import io.sentry.unmarshaller.JsonUnmarshaller;
import io.sentry.unmarshaller.event.UnmarshalledEvent;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
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
    public static final String AUTH_HEADER = "X-Sentry-Auth";
    public static final StringValuePattern AUTH_HEADER_PATTERN = new RegexPattern("Sentry sentry_version=6,sentry_client=sentry-java/[\\w\\-\\.]+,sentry_key=8292bf61d620417282e68a72ae03154a,sentry_secret=e3908e05ad874b24b7a168992bfa3577");

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8080));

    public void stubForProject1Store(int responseCode) {
        wireMockRule.stubFor(
            post(urlEqualTo(PROJECT1_STORE_URL))
                .withHeader(AUTH_HEADER, AUTH_HEADER_PATTERN)
                .withHeader("Content-Type", new EqualToPattern("application/json"))
                .withHeader("Content-Encoding", new EqualToPattern("gzip"))
                .willReturn(aResponse().withStatus(responseCode)));
    }

    public void stub200ForProject1Store() {
        stubForProject1Store(200);
    }

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
            if (serveEvent.getResponse().getStatus() == 200) {
                UnmarshalledEvent event = unmarshaller.unmarshal(new ByteArrayInputStream(serveEvent.getRequest().getBody()));
                if (event != null) {
                    events.add(event);
                }
            }
        }

        return events;
    }

}
