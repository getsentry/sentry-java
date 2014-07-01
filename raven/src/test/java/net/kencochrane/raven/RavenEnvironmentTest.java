package net.kencochrane.raven;

import net.kencochrane.raven.environment.RavenEnvironment;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RavenEnvironmentTest {
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
