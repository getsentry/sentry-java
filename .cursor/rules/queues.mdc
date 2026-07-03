---
alwaysApply: false
description: Sentry Queues module and Java SDK queue tracing
---
# Sentry Queues and Java SDK Queue Tracing

## Product model

Sentry Queues is built from tracing data. SDKs mark queue work with queue-specific span operations and messaging span data so Sentry can identify producers, consumers, destinations, latency, and failures.

The important concepts are:
- `queue.publish`: a span for enqueueing/publishing a message to a queue or topic.
- `queue.process`: a transaction for processing a dequeued message.
- Messaging span data, especially:
  - `messaging.system` (for example `kafka`)
  - `messaging.destination.name` (queue/topic name)
  - `messaging.message.id`
  - `messaging.message.retry.count`
  - `messaging.message.body.size`
  - `messaging.message.envelope.size`
  - `messaging.message.receive.latency`
- Distributed tracing headers (`sentry-trace` and `baggage`) link producer-side work to consumer-side processing.
- Queue receive latency is the time a message spent waiting between publish/enqueue and processing. For Java Kafka, this comes from the `sentry-task-enqueued-time` header that the producer writes and the consumer reads.

The Queues UI is not backed by a separate Java event type. The Java SDK contributes data through spans/transactions with the expected operations, trace context, statuses, and messaging attributes.

## Java SDK implementation

Queue tracing is opt-in. `SentryOptions.isEnableQueueTracing()` defaults to `false` and can be enabled with `setEnableQueueTracing(true)` or external config key `enable-queue-tracing` (`sentry.enable-queue-tracing` in Spring Boot). Captured queue spans/transactions still depend on tracing being enabled and sampled.

Kafka support lives in `sentry-kafka`:
- `SentryKafkaProducer.wrap(Producer)` wraps Kafka `Producer.send(...)` calls.
  - Creates a `queue.publish` child span when there is an active span.
  - Sets `messaging.system=kafka` and `messaging.destination.name=<topic>`.
  - Injects `sentry-trace`, `baggage`, and `sentry-task-enqueued-time` headers.
  - Still injects tracing/enqueued-time headers when queue tracing is enabled but there is no active span, so background producers can link to consumers.
  - Finishes the span from the Kafka callback with `OK` or `INTERNAL_ERROR`.
- `SentryKafkaConsumerTracing.withTracing(record, callback)` is the manual raw-Kafka consumer helper.
  - Forks root scopes for the processing lifecycle and makes them current.
  - Continues the trace from Kafka headers.
  - Starts a `queue.process` transaction bound to scope when tracing is enabled.
  - Sets Kafka messaging data, body size, retry count, and receive latency when available.
  - Finishes with `OK` or `INTERNAL_ERROR` and never lets instrumentation failures break customer processing.

Spring Kafka support lives in `sentry-spring`, `sentry-spring-jakarta`, and `sentry-spring-7`:
- `SentryKafkaProducerBeanPostProcessor` installs a producer post-processor on `DefaultKafkaProducerFactory` and wraps created producers with `SentryKafkaProducer.wrap(...)`.
- `SentryKafkaConsumerBeanPostProcessor` installs `SentryKafkaRecordInterceptor` on listener container factories.
- `SentryKafkaRecordInterceptor` starts/finishes `queue.process` transactions around listener processing, continues traces from headers, forks scopes for the record lifecycle, and preserves any existing delegate interceptor.
- Spring Boot auto-configuration registers both post-processors only when Spring Kafka and `sentry-kafka` are present and `sentry.enable-queue-tracing=true`.
- Spring Boot queue auto-configuration is disabled when Sentry OpenTelemetry integration classes are present to avoid duplicate Kafka instrumentation.

## Trace origins and suppression

Queue instrumentation sets span origins so it can be identified and suppressed with `ignoredSpanOrigins`:
- Raw Kafka producer: `auto.queue.kafka.producer`
- Raw Kafka consumer helper: `manual.queue.kafka.consumer`
- Spring Kafka producer: `auto.queue.spring.kafka.producer`, `auto.queue.spring_jakarta.kafka.producer`, `auto.queue.spring7.kafka.producer`
- Spring Kafka consumer: `auto.queue.spring.kafka.consumer`, `auto.queue.spring_jakarta.kafka.consumer`, `auto.queue.spring7.kafka.consumer`

## Files to inspect when changing queue tracing

- Core option and conventions:
  - `sentry/src/main/java/io/sentry/SentryOptions.java`
  - `sentry/src/main/java/io/sentry/ExternalOptions.java`
  - `sentry/src/main/java/io/sentry/SpanDataConvention.java`
- Raw Kafka:
  - `sentry-kafka/src/main/java/io/sentry/kafka/SentryKafkaProducer.java`
  - `sentry-kafka/src/main/java/io/sentry/kafka/SentryKafkaConsumerTracing.java`
  - `sentry-kafka/src/test/kotlin/io/sentry/kafka/*Test.kt`
- Spring Kafka:
  - `sentry-spring*/src/main/java/io/sentry/**/kafka/*`
  - `sentry-spring*/src/test/kotlin/io/sentry/**/kafka/*Test.kt`
  - `sentry-spring-boot*/src/main/java/io/sentry/**/SentryAutoConfiguration.java`
  - `sentry-spring-boot*/src/test/kotlin/io/sentry/**/SentryKafkaAutoConfigurationTest.kt`

## Related rules

Also fetch:
- `options` when changing `enableQueueTracing` or configuration surfaces.
- `scopes` when changing consumer scope forking/lifecycle.
- `opentelemetry` when changing coexistence with OTel auto-instrumentation.
- `api` when changing public Kafka APIs or option methods.
