package io.sentry.environment;

import io.sentry.BaseTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryEnvironmentTest extends BaseTest {
    @AfterMethod
    public void tearDown() throws Exception {
        SentryEnvironment.SENTRY_THREAD.remove();
    }

    @Test
    public void testThreadNotManagedByDefault() throws Exception {
        assertThat(SentryEnvironment.isManagingThread(), is(false));
    }

    @Test
    public void testStartManagingThreadWorks() throws Exception {
        SentryEnvironment.startManagingThread();

        assertThat(SentryEnvironment.isManagingThread(), is(true));
    }

    @Test
    public void testStartManagingAlreadyManagedThreadWorks() throws Exception {
        SentryEnvironment.startManagingThread();

        SentryEnvironment.startManagingThread();

        assertThat(SentryEnvironment.isManagingThread(), is(true));
    }

    @Test
    public void testStopManagingThreadWorks() throws Exception {
        SentryEnvironment.startManagingThread();

        SentryEnvironment.stopManagingThread();

        assertThat(SentryEnvironment.isManagingThread(), is(false));
    }

    @Test
    public void testStopManagingNonManagedThreadWorks() throws Exception {
        SentryEnvironment.stopManagingThread();

        assertThat(SentryEnvironment.isManagingThread(), is(false));
    }

    @Test
    public void testThreadManagedTwiceNeedsToBeUnmanagedTwice() throws Exception {
        SentryEnvironment.startManagingThread();
        SentryEnvironment.startManagingThread();

        SentryEnvironment.stopManagingThread();
        assertThat(SentryEnvironment.isManagingThread(), is(true));
        SentryEnvironment.stopManagingThread();
        assertThat(SentryEnvironment.isManagingThread(), is(false));
    }
}
