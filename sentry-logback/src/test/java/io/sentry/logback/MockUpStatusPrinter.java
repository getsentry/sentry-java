package io.sentry.logback;

import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.StatusPrinter;
import mockit.Mock;
import mockit.MockUp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockUpStatusPrinter extends MockUp<StatusPrinter> {
    private static final Logger logger = LoggerFactory.getLogger("ErrorHandler");

    @Mock
    public static void buildStr(StringBuilder sb, String indentation, Status s) {
        switch (s.getEffectiveLevel()) {
            case Status.INFO:
                logger.info("{} - {}", s.getOrigin(), s.getMessage());
                return;
            case Status.WARN:
                logger.warn("{} - {}", s.getOrigin(), s.getMessage());
                return;
            case Status.ERROR:
                logger.error("{} - {}", s.getOrigin(), s.getMessage());
                return;
            default:
                logger.debug("{} - {}", s.getOrigin(), s.getMessage());
                return;
        }
    }
}
