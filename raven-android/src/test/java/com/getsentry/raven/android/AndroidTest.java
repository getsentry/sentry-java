package com.getsentry.raven.android;

import com.getsentry.raven.BaseIT;
import com.getsentry.raven.Raven;
import org.junit.After;
import org.junit.Before;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class AndroidTest extends BaseIT {

    @Before
    public void setup() {
        stub200ForProject1Store();
    }

    @After
    public void tearDown() throws Exception {
        Raven.clearStoredRaven();
    }

}