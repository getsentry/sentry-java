package com.getsentry.raven.environment;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RavenEnvironmentTest {
    @AfterMethod
    public void tearDown() throws Exception {
        RavenEnvironment.RAVEN_THREAD.remove();
    }

    @Test
    public void testThreadNotManagedByDefault() throws Exception {
        assertThat(RavenEnvironment.isManagingThread(), is(false));
    }

    @Test
    public void testStartManagingThreadWorks() throws Exception {
        RavenEnvironment.startManagingThread();

        assertThat(RavenEnvironment.isManagingThread(), is(true));
    }

    @Test
    public void testStartManagingAlreadyManagedThreadWorks() throws Exception {
        RavenEnvironment.startManagingThread();

        RavenEnvironment.startManagingThread();

        assertThat(RavenEnvironment.isManagingThread(), is(true));
    }

    @Test
    public void testStopManagingThreadWorks() throws Exception {
        RavenEnvironment.startManagingThread();

        RavenEnvironment.stopManagingThread();

        assertThat(RavenEnvironment.isManagingThread(), is(false));
    }

    @Test
    public void testStopManagingNonManagedThreadWorks() throws Exception {
        RavenEnvironment.stopManagingThread();

        assertThat(RavenEnvironment.isManagingThread(), is(false));
    }

    @Test
    public void testThreadManagedTwiceNeedsToBeUnmanagedTwice() throws Exception {
        RavenEnvironment.startManagingThread();
        RavenEnvironment.startManagingThread();

        RavenEnvironment.stopManagingThread();
        assertThat(RavenEnvironment.isManagingThread(), is(true));
        RavenEnvironment.stopManagingThread();
        assertThat(RavenEnvironment.isManagingThread(), is(false));
    }
}
