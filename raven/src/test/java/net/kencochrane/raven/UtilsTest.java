package net.kencochrane.raven;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for methods from {@link Utils}.
 */
public class UtilsTest {

    @Test
    public void compress_decompress() {
        final String s = "this is a string - no really!";
        Assert.assertEquals(s, Utils.fromUtf8(Utils.decompress(Utils.compress(Utils.toUtf8(s)))));
    }

}
