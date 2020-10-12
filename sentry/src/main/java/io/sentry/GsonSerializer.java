package io.sentry;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.sentry.adapters.ContextsDeserializerAdapter;
import io.sentry.adapters.ContextsSerializerAdapter;
import io.sentry.adapters.DateDeserializerAdapter;
import io.sentry.adapters.DateSerializerAdapter;
import io.sentry.adapters.OrientationDeserializerAdapter;
import io.sentry.adapters.OrientationSerializerAdapter;
import io.sentry.adapters.SentryIdDeserializerAdapter;
import io.sentry.adapters.SentryIdSerializerAdapter;
import io.sentry.adapters.SentryLevelDeserializerAdapter;
import io.sentry.adapters.SentryLevelSerializerAdapter;
import io.sentry.adapters.SpanIdDeserializerAdapter;
import io.sentry.adapters.SpanIdSerializerAdapter;
import io.sentry.adapters.SpanStatusDeserializerAdapter;
import io.sentry.adapters.SpanStatusSerializerAdapter;
import io.sentry.adapters.TimeZoneDeserializerAdapter;
import io.sentry.adapters.TimeZoneSerializerAdapter;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Device;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The AndroidSerializer class that uses Gson as JSON parser */
public final class GsonSerializer implements ISerializer {

  /** the UTF-8 Charset */
  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /** the ILogger interface */
  private final @NotNull ILogger logger;

  /** the Gson instance */
  private final @NotNull Gson gson;

  /** the IEnvelopeReader interface */
  private final @NotNull IEnvelopeReader envelopeReader;

  /**
   * AndroidSerializer ctor
   *
   * @param logger the ILogger interface
   * @param envelopeReader the IEnvelopeReader interface
   */
  public GsonSerializer(
      final @NotNull ILogger logger, final @NotNull IEnvelopeReader envelopeReader) {
    this.logger = Objects.requireNonNull(logger, "The ILogger object is required.");
    this.envelopeReader =
        Objects.requireNonNull(envelopeReader, "The IEnvelopeReader object is required.");

    gson = provideGson();
  }

  /**
   * Creates a Gson instance with the naming policy and adapters
   *
   * @return the Gson instance
   */
  private @NotNull Gson provideGson() {
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
        .registerTypeAdapter(Contexts.class, new ContextsSerializerAdapter(logger))
        .registerTypeAdapterFactory(UnknownPropertiesTypeAdapterFactory.get())
        .registerTypeAdapter(SentryEnvelopeHeader.class, new SentryEnvelopeHeaderAdapter())
        .registerTypeAdapter(SentryEnvelopeItemHeader.class, new SentryEnvelopeItemHeaderAdapter())
        .registerTypeAdapter(Session.class, new SessionAdapter(logger))
        .registerTypeAdapter(SpanId.class, new SpanIdDeserializerAdapter(logger))
        .registerTypeAdapter(SpanId.class, new SpanIdSerializerAdapter(logger))
        .registerTypeAdapter(SpanStatus.class, new SpanStatusDeserializerAdapter(logger))
        .registerTypeAdapter(SpanStatus.class, new SpanStatusSerializerAdapter(logger))
        .create();
  }

  /**
   * Deserialize a SentryEvent from a stream Reader (JSON)
   *
   * @param reader the Reader
   * @return the SentryEvent class or null
   */
  @Override
  public @Nullable SentryEvent deserializeEvent(final @NotNull Reader reader) {
    Objects.requireNonNull(reader, "The Reader object is required.");

    return gson.fromJson(reader, SentryEvent.class);
  }

  /**
   * Deserialize a Session from a stream Reader (JSON)
   *
   * @param reader the Reader
   * @return the SentryEvent class or null
   */
  @Override
  public @Nullable Session deserializeSession(final @NotNull Reader reader) {
    Objects.requireNonNull(reader, "The Reader object is required.");

    return gson.fromJson(reader, Session.class);
  }

  /**
   * Deserialize a SentryEnvelope from a InputStream (Envelope+JSON)
   *
   * @param inputStream the InputStream
   * @return the SentryEnvelope class or null
   */
  @Override
  public @Nullable SentryEnvelope deserializeEnvelope(final @NotNull InputStream inputStream) {
    Objects.requireNonNull(inputStream, "The InputStream object is required.");
    try {
      return envelopeReader.read(inputStream);
    } catch (IOException e) {
      logger.log(SentryLevel.ERROR, "Error deserializing envelope.", e);
      return null;
    }
  }

  @Override
  public <T> void serialize(final @NotNull T entity, final @NotNull Writer writer)
      throws IOException {
    Objects.requireNonNull(entity, "The entity is required.");
    Objects.requireNonNull(writer, "The Writer object is required.");

    if (logger.isEnabled(SentryLevel.DEBUG)) {
      logger.log(SentryLevel.DEBUG, "Serializing object: %s", gson.toJson(entity));
    }
    gson.toJson(entity, entity.getClass(), writer);

    writer.flush();
  }

  /**
   * Serialize a SentryEnvelope to a stream Writer (JSON)
   *
   * @param envelope the SentryEnvelope
   * @param writer the Writer
   * @throws IOException an IOException
   */
  @Override
  public void serialize(final @NotNull SentryEnvelope envelope, final @NotNull Writer writer)
      throws Exception {
    Objects.requireNonNull(envelope, "The SentryEnvelope object is required.");
    Objects.requireNonNull(writer, "The Writer object is required.");

    gson.toJson(envelope.getHeader(), SentryEnvelopeHeader.class, writer);
    writer.write("\n");
    for (SentryEnvelopeItem item : envelope.getItems()) {
      gson.toJson(item.getHeader(), SentryEnvelopeItemHeader.class, writer);
      writer.write("\n");

      try (final BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8))) {
        final char[] buffer = new char[1024];
        int charsRead;
        while ((charsRead = reader.read(buffer, 0, buffer.length)) > 0) {
          writer.write(buffer, 0, charsRead);
        }
      }

      writer.write("\n");
    }
    writer.flush();
  }

  /**
   * Serialize a Map to a String
   *
   * @param data the data Map
   * @return the serialized String
   * @throws Exception the Exception if there was a problem during serialization
   */
  @Override
  public String serialize(final @NotNull Map<String, Object> data) throws Exception {
    Objects.requireNonNull(data, "The SentryEnvelope object is required.");

    return gson.toJson(data);
  }
}
