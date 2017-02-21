package com.getsentry.raven.connection;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.fail;

import com.getsentry.raven.BaseTest2;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.marshaller.json.JsonMarshaller;
import org.junit.Test;

import java.net.URISyntaxException;

public class HttpConnectionTest2 extends BaseTest2 {

    private HttpConnection getHttpConnection() throws URISyntaxException {
        HttpConnection httpConnection = new HttpConnection(getSentryStoreUrl(), "public", "private",
            null, null);
        httpConnection.setMarshaller(new JsonMarshaller());
        return httpConnection;
    }

    @Test
    public void testBackoffAfter429() throws URISyntaxException {
        wireMockRule
            .stubFor(post(urlEqualTo(STORE_URL))
            .willReturn(aResponse().withStatus(429)));

        HttpConnection httpConnection = getHttpConnection();
        Event event = new EventBuilder().build();

        try {
            httpConnection.send(event);
            fail("Expected ConnectionException to be thrown");
        } catch (ConnectionException e) {
            // expected
        }

        wireMockRule
            .verify(postRequestedFor(urlMatching(STORE_URL)));

        // TODO: this shouldn't throw an error, which is good (because its in backoff)
        //       but it's bad because we're depending on the clock not ticking over 1
        //       second -- what I want/need is a way to replace the clock in tests
        httpConnection.send(event);
    }

}
