package com.getsentry.raven.connection;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.fail;

import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.marshaller.json.JsonMarshaller;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class HttpConnectionTest2 {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private String getDsn(String projectId) {
        return "http://public:private@localhost:" + wireMockRule.port() + "/" + projectId;
    }

    private URI getSentryUri() throws URISyntaxException {
        return new URI("http://localhost:" + wireMockRule.port() + "/");
    }

    private URL getSentryStoreUrl(String projectId) throws URISyntaxException {
        return HttpConnection.getSentryApiUrl(getSentryUri(), "1");
    }

    @Test
    public void test() throws URISyntaxException {
        wireMockRule
            .stubFor(post(urlEqualTo("/api/1/store/"))
            .willReturn(aResponse().withStatus(429)));

        HttpConnection httpConnection = new HttpConnection(
            getSentryStoreUrl("1"),
            "public",
            "private",
            null,
            null);
        httpConnection.setMarshaller(new JsonMarshaller());

        try {
            httpConnection.send(new EventBuilder().build());
            fail("Expected ConnectionException to be thrown");
        } catch (ConnectionException e) {
            // expected
        }

        wireMockRule
            .verify(postRequestedFor(urlMatching("/api/1/store/")));

        // TODO: this shouldn't throw an error, which is good (because its in backoff)
        //       but it's bad because we're depending on the clock not ticking over 1
        //       second -- what I want/need is a way to replace the clock in tests
        httpConnection.send(new EventBuilder().build());
    }

}
