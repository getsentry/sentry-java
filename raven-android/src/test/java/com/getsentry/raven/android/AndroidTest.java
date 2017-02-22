package com.getsentry.raven.android;

import com.getsentry.raven.BaseIT;
import org.junit.After;
import org.junit.Before;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class AndroidTest extends BaseIT {

    @Before
    public void setup() {
        wireMockRule.stubFor(post(urlEqualTo(PROJECT1_STORE_URL)).willReturn(aResponse().withStatus(200)));
    }

    @After
    public void tearDown() throws Exception {
        Raven.clearStoredRaven();
    }

}