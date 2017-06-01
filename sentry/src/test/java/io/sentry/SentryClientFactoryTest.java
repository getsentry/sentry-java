package io.sentry;

import mockit.*;
import io.sentry.dsn.Dsn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ServiceLoader;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SentryClientFactoryTest extends BaseTest {
    @Test
    public void testSentryClientForFactoryNameSucceedsIfFactoryFound() throws Exception {
        String dsn = "noop://localhost/1?factory=io.sentry.TestFactory";
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn);
        assertThat(sentryClient.getRelease(), is("312407214120"));
    }

    @Test
    public void testSentryClientForFactoryReturnsNullIfNoFactoryFound() throws Exception {
        String dsn = "noop://localhost/1?factory=invalid";
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn);
        assertThat(sentryClient, is(nullValue()));
    }

    @Test
    public void testAutoDetectDsnIfNotProvided() throws Exception {
        SentryClient sentryClient;
        String propName = "sentry.dsn";
        String previous = System.getProperty(propName);
        try {
            System.setProperty(propName, "noop://localhost/1?release=xyz");
            sentryClient = SentryClientFactory.sentryClient(null);
        } finally {
            if (previous == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, previous);
            }
        }

        assertThat(sentryClient.getRelease(), is("xyz"));
    }

    @Test
    public void testCreateDsnIfStringProvided() throws Exception {
        final String dsn = "noop://localhost/1?release=abc";
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn);
        assertThat(sentryClient.getRelease(), is("abc"));
    }
}
