package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.Objects;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public abstract class RRWebIncrementalSnapshotEvent extends RRWebEvent {

  public enum IncrementalSource implements JsonSerializable {
    Mutation,
    MouseMove,
    MouseInteraction,
    Scroll,
    ViewportResize,
    Input,
    TouchMove,
    MediaInteraction,
    StyleSheetRule,
    CanvasMutation,
    Font,
    Log,
    Drag,
    StyleDeclaration,
    Selection,
    AdoptedStyleSheet,
    CustomElement;

    @Override
    public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger)
        throws IOException {
      writer.value(ordinal());
    }

    public static final class Deserializer implements JsonDeserializer<IncrementalSource> {
      @Override
      public @NotNull IncrementalSource deserialize(
          final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
        return IncrementalSource.values()[reader.nextInt()];
      }
    }
  }

  private IncrementalSource source;

  public RRWebIncrementalSnapshotEvent(final @NotNull IncrementalSource source) {
    super(RRWebEventType.IncrementalSnapshot);
    this.source = source;
  }

  public IncrementalSource getSource() {
    return source;
  }

  public void setSource(final IncrementalSource source) {
    this.source = source;
  }

  // region json
  public static final class JsonKeys {
    public static final String SOURCE = "source";
  }

  public static final class Serializer {
    public void serialize(
        final @NotNull RRWebIncrementalSnapshotEvent baseEvent,
        final @NotNull ObjectWriter writer,
        final @NotNull ILogger logger)
        throws IOException {
      writer.name(JsonKeys.SOURCE).value(logger, baseEvent.source);
    }
  }

  public static final class Deserializer {
    public boolean deserializeValue(
        final @NotNull RRWebIncrementalSnapshotEvent baseEvent,
        final @NotNull String nextName,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      if (nextName.equals(JsonKeys.SOURCE)) {
        baseEvent.source =
            Objects.requireNonNull(
                reader.nextOrNull(logger, new IncrementalSource.Deserializer()), "");
        return true;
      }
      return false;
    }
  }
  // endregion json
}
