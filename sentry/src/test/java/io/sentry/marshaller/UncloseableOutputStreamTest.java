package io.sentry.marshaller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.sentry.BaseTest;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;

public class UncloseableOutputStreamTest extends BaseTest {
    private Marshaller.UncloseableOutputStream uncloseableOutputStream = null;
    private OutputStream mockOutputStream = null;

    @Before
    public void setup() {
        mockOutputStream = mock(OutputStream.class);
        uncloseableOutputStream = new Marshaller.UncloseableOutputStream(mockOutputStream);
    }

    @Test
    public void testWriteSingleByte() throws Exception {
        final int i = 12;

        uncloseableOutputStream.write(i);

        verify(mockOutputStream).write(eq(i));
    }

    @Test
    public void testWriteByteArray() throws Exception {
        final byte[] array = new byte[0];

        uncloseableOutputStream.write(array);

        verify(mockOutputStream).write(eq(array));
    }

    @Test
    public void testWritePartOfByteArray() throws Exception {
        final byte[] array = new byte[0];
        final int off = 93;
        final int len = 42;

        uncloseableOutputStream.write(array, off, len);

        verify(mockOutputStream).write(eq(array), eq(off), eq(len));
    }

    @Test
    public void testFlush() throws Exception {
        uncloseableOutputStream.flush();

        verify(mockOutputStream).flush();
    }

    @Test
    public void testClose() throws Exception {
        uncloseableOutputStream.close();

        verify(mockOutputStream, never()).close();
    }
}
