package io.sentry;

import io.sentry.clientreport.ClientReport;
import io.sentry.profilemeasurements.ProfileMeasurement;
import io.sentry.profilemeasurements.ProfileMeasurementValue;
import io.sentry.protocol.App;
import io.sentry.protocol.Browser;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Device;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.Geo;
import io.sentry.protocol.Gpu;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Message;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkInfo;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryPackage;
import io.sentry.protocol.SentryRuntime;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.protocol.ViewHierarchy;
import io.sentry.protocol.ViewHierarchyNode;
import io.sentry.rrweb.RRWebBreadcrumbEvent;
import io.sentry.rrweb.RRWebEventType;
import io.sentry.rrweb.RRWebInteractionEvent;
import io.sentry.rrweb.RRWebInteractionMoveEvent;
import io.sentry.rrweb.RRWebMetaEvent;
import io.sentry.rrweb.RRWebSpanEvent;
import io.sentry.rrweb.RRWebVideoEvent;
import io.sentry.util.Objects;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The serializer class that uses manual JSON parsing with the help of vendored GSON reader/writer
 * classes.
 */
public final class JsonSerializer implements ISerializer {

  /** the UTF-8 Charset */
  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /** the SentryOptions */
  private final @NotNull SentryOptions options;

  private final @NotNull Map<Class<?>, JsonDeserializer<?>> deserializersByClass;

  /**
   * All our custom deserializers need to be registered to be used with the deserializer instance. *
   */
  public JsonSerializer(@NotNull SentryOptions options) {
    this.options = options;

    deserializersByClass = new HashMap<>();
    deserializersByClass.put(App.class, new App.Deserializer());
    deserializersByClass.put(Breadcrumb.class, new Breadcrumb.Deserializer());
    deserializersByClass.put(Browser.class, new Browser.Deserializer());
    deserializersByClass.put(Contexts.class, new Contexts.Deserializer());
    deserializersByClass.put(DebugImage.class, new DebugImage.Deserializer());
    deserializersByClass.put(DebugMeta.class, new DebugMeta.Deserializer());
    deserializersByClass.put(Device.class, new Device.Deserializer());
    deserializersByClass.put(
        Device.DeviceOrientation.class, new Device.DeviceOrientation.Deserializer());
    deserializersByClass.put(Feedback.class, new Feedback.Deserializer());
    deserializersByClass.put(Gpu.class, new Gpu.Deserializer());
    deserializersByClass.put(MeasurementValue.class, new MeasurementValue.Deserializer());
    deserializersByClass.put(Mechanism.class, new Mechanism.Deserializer());
    deserializersByClass.put(Message.class, new Message.Deserializer());
    deserializersByClass.put(OperatingSystem.class, new OperatingSystem.Deserializer());
    deserializersByClass.put(ProfileChunk.class, new ProfileChunk.Deserializer());
    deserializersByClass.put(ProfileContext.class, new ProfileContext.Deserializer());
    deserializersByClass.put(ProfilingTraceData.class, new ProfilingTraceData.Deserializer());
    deserializersByClass.put(
        ProfilingTransactionData.class, new ProfilingTransactionData.Deserializer());
    deserializersByClass.put(ProfileMeasurement.class, new ProfileMeasurement.Deserializer());
    deserializersByClass.put(
        ProfileMeasurementValue.class, new ProfileMeasurementValue.Deserializer());
    deserializersByClass.put(Request.class, new Request.Deserializer());
    deserializersByClass.put(ReplayRecording.class, new ReplayRecording.Deserializer());
    deserializersByClass.put(RRWebBreadcrumbEvent.class, new RRWebBreadcrumbEvent.Deserializer());
    deserializersByClass.put(RRWebEventType.class, new RRWebEventType.Deserializer());
    deserializersByClass.put(RRWebInteractionEvent.class, new RRWebInteractionEvent.Deserializer());
    deserializersByClass.put(
        RRWebInteractionMoveEvent.class, new RRWebInteractionMoveEvent.Deserializer());
    deserializersByClass.put(RRWebMetaEvent.class, new RRWebMetaEvent.Deserializer());
    deserializersByClass.put(RRWebSpanEvent.class, new RRWebSpanEvent.Deserializer());
    deserializersByClass.put(RRWebVideoEvent.class, new RRWebVideoEvent.Deserializer());
    deserializersByClass.put(SdkInfo.class, new SdkInfo.Deserializer());
    deserializersByClass.put(SdkVersion.class, new SdkVersion.Deserializer());
    deserializersByClass.put(SentryEnvelopeHeader.class, new SentryEnvelopeHeader.Deserializer());
    deserializersByClass.put(
        SentryEnvelopeItemHeader.class, new SentryEnvelopeItemHeader.Deserializer());
    deserializersByClass.put(SentryEvent.class, new SentryEvent.Deserializer());
    deserializersByClass.put(SentryException.class, new SentryException.Deserializer());
    deserializersByClass.put(SentryItemType.class, new SentryItemType.Deserializer());
    deserializersByClass.put(SentryLevel.class, new SentryLevel.Deserializer());
    deserializersByClass.put(SentryLockReason.class, new SentryLockReason.Deserializer());
    deserializersByClass.put(SentryPackage.class, new SentryPackage.Deserializer());
    deserializersByClass.put(SentryRuntime.class, new SentryRuntime.Deserializer());
    deserializersByClass.put(SentryReplayEvent.class, new SentryReplayEvent.Deserializer());
    deserializersByClass.put(SentrySpan.class, new SentrySpan.Deserializer());
    deserializersByClass.put(SentryStackFrame.class, new SentryStackFrame.Deserializer());
    deserializersByClass.put(SentryStackTrace.class, new SentryStackTrace.Deserializer());
    deserializersByClass.put(
        SentryAppStartProfilingOptions.class, new SentryAppStartProfilingOptions.Deserializer());
    deserializersByClass.put(SentryThread.class, new SentryThread.Deserializer());
    deserializersByClass.put(SentryTransaction.class, new SentryTransaction.Deserializer());
    deserializersByClass.put(Session.class, new Session.Deserializer());
    deserializersByClass.put(SpanContext.class, new SpanContext.Deserializer());
    deserializersByClass.put(SpanId.class, new SpanId.Deserializer());
    deserializersByClass.put(SpanStatus.class, new SpanStatus.Deserializer());
    deserializersByClass.put(User.class, new User.Deserializer());
    deserializersByClass.put(Geo.class, new Geo.Deserializer());
    deserializersByClass.put(UserFeedback.class, new UserFeedback.Deserializer());
    deserializersByClass.put(ClientReport.class, new ClientReport.Deserializer());
    deserializersByClass.put(ViewHierarchyNode.class, new ViewHierarchyNode.Deserializer());
    deserializersByClass.put(ViewHierarchy.class, new ViewHierarchy.Deserializer());
  }

  // Deserialize

  @SuppressWarnings("unchecked")
  @Override
  public <T, R> @Nullable T deserializeCollection(
      @NotNull Reader reader,
      @NotNull Class<T> clazz,
      @Nullable JsonDeserializer<R> elementDeserializer) {
    try (JsonObjectReader jsonObjectReader = new JsonObjectReader(reader)) {
      if (Collection.class.isAssignableFrom(clazz)) {
        if (elementDeserializer == null) {
          // if the object has no known deserializer we do best effort and deserialize it as map
          return (T) jsonObjectReader.nextObjectOrNull();
        }

        return (T) jsonObjectReader.nextListOrNull(options.getLogger(), elementDeserializer);
      } else {
        return (T) jsonObjectReader.nextObjectOrNull();
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when deserializing", e);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> @Nullable T deserialize(@NotNull Reader reader, @NotNull Class<T> clazz) {
    try (JsonObjectReader jsonObjectReader = new JsonObjectReader(reader)) {
      JsonDeserializer<?> deserializer = deserializersByClass.get(clazz);
      if (deserializer != null) {
        Object object = deserializer.deserialize(jsonObjectReader, options.getLogger());
        return clazz.cast(object);
      } else if (isKnownPrimitive(clazz)) {
        return (T) jsonObjectReader.nextObjectOrNull();
      } else {
        return null; // No way to deserialize objects we don't know about.
      }
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Error when deserializing", e);
      return null;
    }
  }

  @Override
  public @Nullable SentryEnvelope deserializeEnvelope(@NotNull InputStream inputStream) {
    Objects.requireNonNull(inputStream, "The InputStream object is required.");
    try {
      return options.getEnvelopeReader().read(inputStream);
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Error deserializing envelope.", e);
      return null;
    }
  }

  // Serialize

  @Override
  public <T> void serialize(@NotNull T entity, @NotNull Writer writer) throws IOException {
    Objects.requireNonNull(entity, "The entity is required.");
    Objects.requireNonNull(writer, "The Writer object is required.");

    if (options.getLogger().isEnabled(SentryLevel.DEBUG)) {
      String serialized = serializeToString(entity, options.isEnablePrettySerializationOutput());
      options.getLogger().log(SentryLevel.DEBUG, "Serializing object: %s", serialized);
    }
    JsonObjectWriter jsonObjectWriter = new JsonObjectWriter(writer, options.getMaxDepth());
    jsonObjectWriter.value(options.getLogger(), entity);
    writer.flush();
  }

  /**
   * Serializes an envelope to an OutputStream
   *
   * @param envelope the envelope
   * @param outputStream will not be closed automatically
   * @throws Exception an exception
   */
  @Override
  public void serialize(@NotNull SentryEnvelope envelope, @NotNull OutputStream outputStream)
      throws Exception {
    Objects.requireNonNull(envelope, "The SentryEnvelope object is required.");
    Objects.requireNonNull(outputStream, "The Stream object is required.");

    // we do not want to close these as we would also close the stream that was passed in
    final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
    final Writer writer = new BufferedWriter(new OutputStreamWriter(bufferedOutputStream, UTF_8));

    try {
      envelope
          .getHeader()
          .serialize(new JsonObjectWriter(writer, options.getMaxDepth()), options.getLogger());
      writer.write("\n");

      for (final SentryEnvelopeItem item : envelope.getItems()) {
        try {
          // When this throws we don't write anything and continue with the next item.
          final byte[] data = item.getData();

          item.getHeader()
              .serialize(new JsonObjectWriter(writer, options.getMaxDepth()), options.getLogger());
          writer.write("\n");
          writer.flush();

          outputStream.write(data);

          writer.write("\n");
        } catch (Exception exception) {
          options
              .getLogger()
              .log(SentryLevel.ERROR, "Failed to create envelope item. Dropping it.", exception);
        }
      }
    } finally {
      writer.flush();
    }
  }

  @Override
  public @NotNull String serialize(@NotNull Map<String, Object> data) throws Exception {
    return serializeToString(data, false);
  }

  // Helper

  private @NotNull String serializeToString(Object object, boolean pretty) throws IOException {
    StringWriter stringWriter = new StringWriter();
    JsonObjectWriter jsonObjectWriter = new JsonObjectWriter(stringWriter, options.getMaxDepth());
    if (pretty) {
      jsonObjectWriter.setIndent("\t");
    }
    jsonObjectWriter.value(options.getLogger(), object);
    return stringWriter.toString();
  }

  private <T> boolean isKnownPrimitive(final @NotNull Class<T> clazz) {
    return clazz.isArray()
        || Collection.class.isAssignableFrom(clazz)
        || String.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz);
  }
}
