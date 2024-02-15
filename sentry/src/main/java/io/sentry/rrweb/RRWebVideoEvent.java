package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectWriter;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RRWebVideoEvent extends RRWebEvent implements JsonUnknown, JsonSerializable {

  @Override public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger)
    throws IOException {

  }

  @Override public @Nullable Map<String, Object> getUnknown() {
    return null;
  }

  @Override public void setUnknown(@Nullable Map<String, Object> unknown) {

  }

  // rrweb uses camelCase hence the json keys are in camelCase here
  public static final class JsonKeys {
    public static final String TAG = "tag";
    public static final String PAYLOAD = "payload";
    public static final String SEGMENT_ID = "segmentId";
    public static final String SIZE = "size";
    public static final String DURATION = "duration";
    public static final String ENCODING = "encoding";
    public static final String CONTAINER = "container";
    public static final String HEIGHT = "height";
    public static final String WIDTH = "width";
    public static final String FRAME_COUNT = "frameCount";
    public static final String FRAME_RATE_TYPE = "frameRateType";
    public static final String FRAME_RATE = "frameRate";
    public static final String LEFT = "left";
    public static final String TOP = "top";
  }


}
