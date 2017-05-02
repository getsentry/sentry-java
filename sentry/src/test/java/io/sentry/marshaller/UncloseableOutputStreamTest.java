package io.sentry.marshaller;

import io.sentry.BaseTest;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.Test;

import java.io.OutputStream;

public class UncloseableOutputStreamTest extends BaseTest {
    @Tested
    private Marshaller.UncloseableOutputStream uncloseableOutputStream = null;
    @Injectable
    private OutputStream mockOutputStream = null;

    @Test
    public void testWriteSingleByte() throws Exception {
        final int i = 12;

        uncloseableOutputStream.write(i);

        new Verifications() {{
            mockOutputStream.write(i);
        }};
    }

    @Test
    public void testWriteByteArray() throws Exception {
        final byte[] array = new byte[0];

        uncloseableOutputStream.write(array);

        new Verifications() {{
            mockOutputStream.write(array);
        }};
    }

    @Test
    public void testWritePartOfByteArray() throws Exception {
        final byte[] array = new byte[0];
        final int off = 93;
        final int len = 42;

        uncloseableOutputStream.write(array, off, len);

        new Verifications() {{
            mockOutputStream.write(array, off, len);
        }};
    }

    @Test
    public void testFlush() throws Exception {
        uncloseableOutputStream.flush();

        new Verifications() {{
            mockOutputStream.flush();
        }};

    }

    @Test
    public void testClose() throws Exception {
        uncloseableOutputStream.close();

        new Verifications() {{
            mockOutputStream.close();
            times = 0;
        }};
    }
}
