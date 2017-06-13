package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import org.testng.annotations.Test;

import java.util.Objects;

public class DebugMetaInterfaceTest extends BaseTest {
    @Test
    public void testDebugMeta() throws Exception {
        DebugMetaInterface.DebugImage image1 = new DebugMetaInterface.DebugImage("abcd-efgh");
        DebugMetaInterface.DebugImage image2 = new DebugMetaInterface.DebugImage("ijkl-mnop");
        final DebugMetaInterface debugInterface = new DebugMetaInterface();
        debugInterface.addDebugImage(image1);
        debugInterface.addDebugImage(image2);

        assert Objects.equals(debugInterface.toString(), "DebugMetaInterface{images=[DebugImage{uuid='abcd-efgh', type='proguard'}, DebugImage{uuid='ijkl-mnop', type='proguard'}]}");
    }
}
