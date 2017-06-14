package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class DebugMetaInterfaceTest extends BaseTest {
    @Test
    public void testDebugMeta() throws Exception {
        DebugMetaInterface.DebugImage image1 = new DebugMetaInterface.DebugImage("abcd-efgh");
        DebugMetaInterface.DebugImage image2 = new DebugMetaInterface.DebugImage("ijkl-mnop");
        final DebugMetaInterface debugInterface = new DebugMetaInterface();
        debugInterface.addDebugImage(image1);
        debugInterface.addDebugImage(image2);

        assertThat(debugInterface.getDebugImages(), contains(image1, image2));
    }
}
