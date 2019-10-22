package io.sentry.android.core;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.sentry.android.core.adapters.*;
import io.sentry.core.ILogger;
import io.sentry.core.ISerializer;
import io.sentry.core.SentryEvent;
import io.sentry.core.protocol.Device;
import io.sentry.core.protocol.SentryId;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.TimeZone;

public class AndroidSerializer implements ISerializer {

  private final ILogger logger;
  private final Gson gson;

  public AndroidSerializer(ILogger logger) {
    this.logger = logger;

    gson = provideGson();
  }

  private Gson provideGson() {
    return new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(SentryId.class, new SentryIdSerializerAdapter(logger))
        .registerTypeAdapter(SentryId.class, new SentryIdDeserializerAdapter(logger))
        .registerTypeAdapter(Date.class, new DateSerializerAdapter(logger))
        .registerTypeAdapter(Date.class, new DateDeserializerAdapter(logger))
        .registerTypeAdapter(TimeZone.class, new TimeZoneSerializerAdapter(logger))
        .registerTypeAdapter(TimeZone.class, new TimeZoneDeserializerAdapter(logger))
        .registerTypeAdapter(
            Device.DeviceOrientation.class, new OrientationSerializerAdapter(logger))
        .registerTypeAdapter(
            Device.DeviceOrientation.class, new OrientationDeserializerAdapter(logger))
        .registerTypeAdapterFactory(UnknownPropertiesTypeAdapterFactory.get())
        .create();
  }

  @Override
  public SentryEvent deserializeEvent(String envelope) {
    return gson.fromJson(envelope, SentryEvent.class);
  }

  @Override
  public void serialize(SentryEvent event, Writer writer) throws IOException {
    gson.toJson(event, SentryEvent.class, writer);
    writer.flush();
    writer.close();
  }
}
