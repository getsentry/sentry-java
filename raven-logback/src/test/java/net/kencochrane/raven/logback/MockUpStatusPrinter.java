package net.kencochrane.raven.logback;

import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.StatusPrinter;
import mockit.Mock;
import mockit.MockUp;

public class MockUpStatusPrinter extends MockUp<StatusPrinter> {
    @Mock
    public static void buildStr(StringBuilder sb, String indentation, Status s) {
        sb.append("[RAVEN] ErrorHandler ")
                .append("[").append(getLevel(s)).append("] ")
                .append(s.getOrigin()).append(" - ")
                .append(s.getMessage())
                .append(CoreConstants.LINE_SEPARATOR);
    }

    private static String getLevel(Status s) {
        switch (s.getEffectiveLevel()) {
            case Status.INFO:
                return "INFO";
            case Status.WARN:
                return "WARN";
            case Status.ERROR:
                return "ERROR";
            default:
                return "UNKOWN";
        }
    }
}
