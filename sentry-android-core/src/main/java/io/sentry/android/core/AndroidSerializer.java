package io.sentry.android.core;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.sentry.android.core.adapters.ContextsDeserializerAdapter;
import io.sentry.android.core.adapters.DateDeserializerAdapter;
import io.sentry.android.core.adapters.DateSerializerAdapter;
import io.sentry.android.core.adapters.OrientationDeserializerAdapter;
import io.sentry.android.core.adapters.OrientationSerializerAdapter;
import io.sentry.android.core.adapters.SentryIdDeserializerAdapter;
import io.sentry.android.core.adapters.SentryIdSerializerAdapter;
import io.sentry.android.core.adapters.SentryLevelDeserializerAdapter;
import io.sentry.android.core.adapters.SentryLevelSerializerAdapter;
import io.sentry.android.core.adapters.TimeZoneDeserializerAdapter;
import io.sentry.android.core.adapters.TimeZoneSerializerAdapter;
import io.sentry.core.EnvelopeReader;
import io.sentry.core.IEnvelopeReader;
import io.sentry.core.ILogger;
import io.sentry.core.ISerializer;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEnvelopeHeader;
import io.sentry.core.SentryEnvelopeHeaderAdapter;
import io.sentry.core.SentryEnvelopeItem;
import io.sentry.core.SentryEnvelopeItemHeader;
import io.sentry.core.SentryEnvelopeItemHeaderAdapter;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.Session;
import io.sentry.core.SessionAdapter;
import io.sentry.core.protocol.Contexts;
import io.sentry.core.protocol.Device;
import io.sentry.core.protocol.SentryId;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.TimeZone;
import org.jetbrains.annotations.NotNull;

final class AndroidSerializer implements ISerializer {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull ILogger logger;
  private final Gson gson;

  private final IEnvelopeReader envelopeReader;

  public AndroidSerializer(final @NotNull ILogger logger) {
    this.logger = logger;

    gson = provideGson();
    envelopeReader = new EnvelopeReader();
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
        .registerTypeAdapter(SentryLevel.class, new SentryLevelSerializerAdapter(logger))
        .registerTypeAdapter(SentryLevel.class, new SentryLevelDeserializerAdapter(logger))
        .registerTypeAdapter(Contexts.class, new ContextsDeserializerAdapter(logger))
        .registerTypeAdapterFactory(UnknownPropertiesTypeAdapterFactory.get())
        .registerTypeAdapter(SentryEnvelopeHeader.class, new SentryEnvelopeHeaderAdapter())
        .registerTypeAdapter(SentryEnvelopeItemHeader.class, new SentryEnvelopeItemHeaderAdapter())
        .registerTypeAdapter(Session.class, new SessionAdapter())
        .create();
  }

  @Override
  public SentryEvent deserializeEvent(Reader eventReader) {
    return gson.fromJson(eventReader, SentryEvent.class);
  }

  @Override
  public Session deserializeSession(Reader reader) {
    return gson.fromJson(reader, Session.class);
  }

  @Override
  public SentryEnvelope deserializeEnvelope(InputStream inputStream) {
    try {
      return envelopeReader.read(inputStream);
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error processing envelope.", e);
      return null;
    }
  }

  @Override
  public void serialize(SentryEvent event, Writer writer) throws IOException {
    gson.toJson(event, SentryEvent.class, writer);
    writer.flush();
  }

  @Override
  public void serialize(Session session, Writer writer) throws IOException {
    gson.toJson(session, Session.class, writer);
    writer.flush();
  }

  @Override
  public void serialize(SentryEnvelope envelope, Writer writer) throws Exception {
    gson.toJson(envelope.getHeader(), SentryEnvelopeHeader.class, writer);
    writer.write("\n");
    for (SentryEnvelopeItem item : envelope.getItems()) {
      gson.toJson(item.getHeader(), SentryEnvelopeItemHeader.class, writer);
      writer.write("\n");

      // TODO: fix it
      String data = new String(item.getData(), UTF_8);
      //      writer.write(item.getData(), 0, item.getData().length);
      writer.write(data);

      writer.write("\n");
    }
    writer.flush();
  }
}
