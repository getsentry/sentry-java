package com.getsentry.raven.buffer;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

public class NoopBufferTest extends BufferTest {
    private static File UNWRITABLE_DIR = new File("./raven-test-unwritable-dir");
    private Buffer buffer;

    @BeforeMethod
    public void setup() {
        UNWRITABLE_DIR.mkdir();
        UNWRITABLE_DIR.setWritable(false);

        buffer = DiskBuffer.newDiskBuffer(UNWRITABLE_DIR, 100);
    }

    @AfterMethod
    public void teardown() {
        delete(UNWRITABLE_DIR);
    }

    @Test
    public void test() {
        assertThat(buffer, instanceOf(NoopBuffer.class));
    }
}
