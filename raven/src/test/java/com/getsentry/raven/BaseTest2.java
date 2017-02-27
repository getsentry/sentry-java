package com.getsentry.raven;

import com.getsentry.raven.connection.HttpConnection;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class BaseTest2 extends BaseTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    public static final String PROJECT_ID = "1";
    public static final String STORE_URL = "/api/" + PROJECT_ID + "/store/";

    public String getDsn(String projectId) {
        return "http://public:private@localhost:" + wireMockRule.port() + "/" + projectId;
    }

    public URI getSentryUri() throws URISyntaxException {
        return new URI("http://localhost:" + wireMockRule.port() + "/");
    }

    public URL getSentryStoreUrl() throws URISyntaxException {
        return HttpConnection.getSentryApiUrl(getSentryUri(), PROJECT_ID);
    }


}
