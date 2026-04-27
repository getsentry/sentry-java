package io.sentry.kafka;

import io.sentry.BaggageHeader;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.util.SpanUtils;
import io.sentry.util.TracingUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a Kafka {@link Producer} via {@link Proxy} to record a {@code queue.publish} span around
 * each {@code send} and to inject Sentry trace propagation headers into the produced record.
 *
 * <p>Only the two {@code send} overloads are intercepted; every other {@link Producer} method is
 * forwarded directly to the delegate. Because the wrapper is a dynamic proxy, it is compatible with
 * any Kafka client version — new methods added to the {@link Producer} interface in future Kafka
 * releases are forwarded automatically without recompilation.
 *
 * <p>For raw Kafka usage:
 *
 * <pre>{@code
 * Producer<String, String> producer =
 *     SentryKafkaProducer.wrap(new KafkaProducer<>(props));
 * }</pre>
 *
 * <p>For Spring Kafka, the {@code SentryKafkaProducerBeanPostProcessor} in {@code
 * sentry-spring-jakarta} installs this wrapper automatically via {@code
 * ProducerFactory.addPostProcessor(...)}.
 */
@ApiStatus.Experimental
public final class SentryKafkaProducer {

  public static final @NotNull String TRACE_ORIGIN = "auto.queue.kafka.producer";
  public static final @NotNull String SENTRY_ENQUEUED_TIME_HEADER = "sentry-task-enqueued-time";

  private SentryKafkaProducer() {}

  /**
   * Wraps the given producer with Sentry instrumentation using the global scopes.
   *
   * @param delegate the Kafka producer to wrap
   * @return an instrumented producer that records {@code queue.publish} spans
   * @param <K> the Kafka record key type
   * @param <V> the Kafka record value type
   */
  public static <K, V> @NotNull Producer<K, V> wrap(final @NotNull Producer<K, V> delegate) {
    return wrap(delegate, ScopesAdapter.getInstance(), TRACE_ORIGIN);
  }

  /**
   * Wraps the given producer with Sentry instrumentation using the provided scopes.
   *
   * @param delegate the Kafka producer to wrap
   * @param scopes the Sentry scopes to use for span creation and header injection
   * @return an instrumented producer that records {@code queue.publish} spans
   * @param <K> the Kafka record key type
   * @param <V> the Kafka record value type
   */
  public static <K, V> @NotNull Producer<K, V> wrap(
      final @NotNull Producer<K, V> delegate, final @NotNull IScopes scopes) {
    return wrap(delegate, scopes, TRACE_ORIGIN);
  }

  /**
   * Wraps the given producer with Sentry instrumentation.
   *
   * @param delegate the Kafka producer to wrap
   * @param scopes the Sentry scopes to use for span creation and header injection
   * @param traceOrigin the trace origin to set on created spans
   * @return an instrumented producer that records {@code queue.publish} spans
   * @param <K> the Kafka record key type
   * @param <V> the Kafka record value type
   */
  @SuppressWarnings("unchecked")
  public static <K, V> @NotNull Producer<K, V> wrap(
      final @NotNull Producer<K, V> delegate,
      final @NotNull IScopes scopes,
      final @NotNull String traceOrigin) {
    return (Producer<K, V>)
        Proxy.newProxyInstance(
            delegate.getClass().getClassLoader(),
            new Class<?>[] {Producer.class},
            new SentryProducerHandler<>(delegate, scopes, traceOrigin));
  }

  static final class SentryProducerHandler<K, V> implements InvocationHandler {

    final @NotNull Producer<K, V> delegate;
    private final @NotNull IScopes scopes;
    private final @NotNull String traceOrigin;

    SentryProducerHandler(
        final @NotNull Producer<K, V> delegate,
        final @NotNull IScopes scopes,
        final @NotNull String traceOrigin) {
      this.delegate = delegate;
      this.scopes = scopes;
      this.traceOrigin = traceOrigin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable Object invoke(
        final @NotNull Object proxy, final @NotNull Method method, final @Nullable Object[] args)
        throws Throwable {
      if ("send".equals(method.getName()) && args != null) {
        if (args.length == 1) {
          return instrumentedSend((ProducerRecord<K, V>) args[0], null);
        } else if (args.length == 2) {
          return instrumentedSend((ProducerRecord<K, V>) args[0], (Callback) args[1]);
        }
      }

      if ("toString".equals(method.getName()) && (args == null || args.length == 0)) {
        return "SentryKafkaProducer[delegate=" + delegate + "]";
      }

      try {
        return method.invoke(delegate, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    @SuppressWarnings("unchecked")
    private @NotNull Object instrumentedSend(
        final @NotNull ProducerRecord<K, V> record, final @Nullable Callback callback) {
      if (!scopes.getOptions().isEnableQueueTracing() || isIgnored()) {
        return delegate.send(record, callback);
      }

      final @Nullable ISpan activeSpan = scopes.getSpan();
      if (activeSpan == null || activeSpan.isNoOp()) {
        maybeInjectHeaders(record.headers(), null);
        return delegate.send(record, callback);
      }

      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(traceOrigin);
      final @NotNull ISpan span =
          activeSpan.startChild("queue.publish", record.topic(), spanOptions);

      span.setData(SpanDataConvention.MESSAGING_SYSTEM, "kafka");
      span.setData(SpanDataConvention.MESSAGING_DESTINATION_NAME, record.topic());
      maybeInjectHeaders(record.headers(), span);

      try {
        return delegate.send(record, wrapCallback(callback, span));
      } catch (Throwable t) {
        finishWithError(span, t);
        throw t;
      }
    }

    private @NotNull Callback wrapCallback(
        final @Nullable Callback userCallback, final @NotNull ISpan span) {
      return (metadata, exception) -> {
        try {
          if (exception != null) {
            span.setThrowable(exception);
            span.setStatus(SpanStatus.INTERNAL_ERROR);
          } else {
            span.setStatus(SpanStatus.OK);
          }
        } catch (Throwable t) {
          scopes
              .getOptions()
              .getLogger()
              .log(SentryLevel.ERROR, "Failed to set status on Kafka producer span.", t);
        } finally {
          try {
            span.finish();
          } finally {
            if (userCallback != null) {
              userCallback.onCompletion(metadata, exception);
            }
          }
        }
      };
    }

    private void finishWithError(final @NotNull ISpan span, final @NotNull Throwable t) {
      span.setThrowable(t);
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.finish();
    }

    private boolean isIgnored() {
      return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), traceOrigin);
    }

    private void maybeInjectHeaders(final @NotNull Headers headers, final @Nullable ISpan span) {
      try {
        final @Nullable List<String> existingBaggageHeaders =
            readHeaderValues(headers, BaggageHeader.BAGGAGE_HEADER);
        final @Nullable TracingUtils.TracingHeaders tracingHeaders =
            TracingUtils.trace(scopes, existingBaggageHeaders, span);
        if (tracingHeaders != null) {
          final @NotNull SentryTraceHeader sentryTraceHeader =
              tracingHeaders.getSentryTraceHeader();
          headers.remove(sentryTraceHeader.getName());
          headers.add(
              sentryTraceHeader.getName(),
              sentryTraceHeader.getValue().getBytes(StandardCharsets.UTF_8));

          final @Nullable BaggageHeader baggageHeader = tracingHeaders.getBaggageHeader();
          if (baggageHeader != null) {
            headers.remove(baggageHeader.getName());
            headers.add(
                baggageHeader.getName(), baggageHeader.getValue().getBytes(StandardCharsets.UTF_8));
          }
        }

        headers.remove(SENTRY_ENQUEUED_TIME_HEADER);
        headers.add(
            SENTRY_ENQUEUED_TIME_HEADER,
            DateUtils.doubleToBigDecimal(DateUtils.millisToSeconds(System.currentTimeMillis()))
                .toString()
                .getBytes(StandardCharsets.UTF_8));
      } catch (Throwable t) {
        scopes
            .getOptions()
            .getLogger()
            .log(SentryLevel.ERROR, "Failed to inject Sentry headers into Kafka record.", t);
      }
    }

    private static @Nullable List<String> readHeaderValues(
        final @NotNull Headers headers, final @NotNull String name) {
      @Nullable List<String> values = null;
      for (final @NotNull Header header : headers.headers(name)) {
        final byte @Nullable [] value = header.value();
        if (value != null) {
          if (values == null) {
            values = new ArrayList<>();
          }
          values.add(new String(value, StandardCharsets.UTF_8));
        }
      }
      return values;
    }
  }
}
