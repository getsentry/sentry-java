package io.sentry.event.interfaces;

import java.util.ArrayList;

/**
 * The DebugMeta interface for Sentry allowing to add debug information about ProGuard.
 */
public class DebugMetaInterface implements SentryInterface {

    public static final String DEBUG_META_INTERFACE = "sentry.interfaces.DebugMeta";

    public ArrayList<DebugImage> getDebugImages() {
        return debugImages;
    }

    ArrayList<DebugImage> debugImages = new ArrayList<>();

    public void addDebugImage(DebugImage debugImage) {
        debugImages.add(debugImage);
    }

    @Override
    public String getInterfaceName() {
        return DEBUG_META_INTERFACE;
    }

    @Override
    public int hashCode() {
        return debugImages.hashCode();
    }

    @Override
    public String toString() {
        return "DebugMetaInterface{"
                + "images=" +  debugImages.toString()
                + '}';
    }

    public static class DebugImage {
        private final String uuid;
        private final String type = "proguard";

        public DebugImage(String uuid) {
            this.uuid = uuid;
        }

        public String getUuid() {
            return uuid;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return "DebugImage{"
                    + "uuid='" + uuid+ '\''
                    + ", type='" + type+ '\''
                    + '}';
        }
    }
}
