package io.sentry.samples.console;

import io.sentry.*;
import io.sentry.clientreport.DiscardReason;
import io.sentry.jcache.SentryJCacheWrapper;
import io.sentry.kafka.SentryKafkaConsumerInterceptor;
import io.sentry.kafka.SentryKafkaProducerInterceptor;
import io.sentry.protocol.Message;
import io.sentry.protocol.User;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class Main {

  private static long numberOfDiscardedSpansDueToOverflow = 0;

  public static void main(String[] args) throws InterruptedException {
    Sentry.init(
        options -> {
          // NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in
          // your Sentry project/dashboard
          options.setEnableExternalConfiguration(true);
          options.setDsn(
              "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563");

          // All events get assigned to the release. See more at
          // https://docs.sentry.io/workflow/releases/
          options.setRelease("io.sentry.samples.console@3.0.0+1");

          // Modifications to event before it goes out. Could replace the event altogether
          options.setBeforeSend(
              (event, hint) -> {
                // Drop an event altogether:
                if (event.getTag("SomeTag") != null) {
                  return null;
                }
                return event;
              });

          options.setBeforeSendTransaction(
              (transaction, hint) -> {
                // Drop a transaction:
                if (transaction.getTag("SomeTransactionTag") != null) {
                  return null;
                }

                return transaction;
              });

          // Allows inspecting and modifying, returning a new or simply rejecting (returning null)
          options.setBeforeBreadcrumb(
              (breadcrumb, hint) -> {
                // Don't add breadcrumbs with message containing:
                if (breadcrumb.getMessage() != null
                    && breadcrumb.getMessage().contains("bad breadcrumb")) {
                  return null;
                }
                return breadcrumb;
              });

          // Record data being discarded, including the reason, type of data, and the number of
          // items dropped
          options.setOnDiscard(
              (reason, category, number) -> {
                // Only record the number of lost spans due to overflow conditions
                if ((reason.equals(DiscardReason.CACHE_OVERFLOW)
                        || reason.equals(DiscardReason.QUEUE_OVERFLOW))
                    && category.equals(DataCategory.Span)) {
                  numberOfDiscardedSpansDueToOverflow += number;
                }
              });

          // Configure the background worker which sends events to sentry:
          // Wait up to 5 seconds before shutdown while there are events to send.
          options.setShutdownTimeoutMillis(5000);

          // Enable SDK logging with Debug level
          options.setDebug(true);
          // To change the verbosity, use:
          // By default it's DEBUG.
          // options.setDiagnosticLevel(SentryLevel.ERROR);
          // A good option to have SDK debug log in prod is to use only level ERROR here.

          // Exclude frames from some packages from being "inApp" so are hidden by default in Sentry
          // UI:
          options.addInAppExclude("org.jboss");

          // Include frames from our package
          options.addInAppInclude("io.sentry.samples");

          // Performance configuration options
          // Set what percentage of traces should be collected
          options.setTracesSampleRate(1.0); // set 0.5 to send 50% of traces

          // Enable cache tracing to create spans for cache operations
          options.setEnableCacheTracing(true);
          options.setEnableQueueTracing(true);

          // Determine traces sample rate based on the sampling context
          //          options.setTracesSampler(
          //              context -> {
          //                // only 10% of transactions with "/product" prefix will be collected
          //                if (!context.getTransactionContext().getName().startsWith("/products"))
          // {
          //                  return 0.1;
          //                } else {
          //                  return 0.5;
          //                }
          //              });
        });

    Sentry.addBreadcrumb(
        "A 'bad breadcrumb' that will be rejected because of 'BeforeBreadcrumb callback above.'");

    // Data added to the root scope (no PushScope called up to this point)
    // The modifications done here will affect all events sent and will propagate to child scopes.
    Sentry.configureScope(
        scope -> {
          scope.addEventProcessor(new SomeEventProcessor());

          scope.setExtra("SomeExtraInfo", "Some value for extra info");
        });

    // Configures a scope which is only valid within the callback
    Sentry.withScope(
        scope -> {
          scope.setLevel(SentryLevel.FATAL);
          scope.setTransaction("main");

          // This message includes the data set to the scope in this block:
          Sentry.captureMessage("Fatal message!");
        });

    // Only data added to the scope on `configureScope` above is included.
    Sentry.captureMessage("Some warning!", SentryLevel.WARNING);

    Sentry.addFeatureFlag("my-feature-flag", true);

    Sentry.setAttribute("user.type", "admin");
    Sentry.setAttribute("feature.version", 2);
    captureMetrics();

    // Sending exception:
    Exception exception = new RuntimeException("Some error!");
    Sentry.captureException(exception);

    // An event with breadcrumb and user data
    SentryEvent evt = new SentryEvent();
    Message msg = new Message();
    msg.setMessage("Detailed event");
    evt.setMessage(msg);
    evt.addBreadcrumb("Breadcrumb directly to the event");
    User user = new User();
    user.setUsername("some@user");
    evt.setUser(user);
    // Group all events with the following fingerprint:
    evt.setFingerprints(Collections.singletonList("NewClientDebug"));
    evt.setLevel(SentryLevel.DEBUG);
    Sentry.captureEvent(evt);

    int count = 10;
    for (int i = 0; i < count; i++) {
      String messageContent = "%d of %d items we'll wait to flush to Sentry!";
      Message message = new Message();
      message.setMessage(messageContent);
      message.setFormatted(String.format(messageContent, i, count));
      SentryEvent event = new SentryEvent();
      event.setMessage(message);

      final Hint hint = new Hint();
      hint.set("level", SentryLevel.DEBUG);
      Sentry.captureEvent(event, hint);
    }

    // Cache tracing with JCache (JSR-107)
    //
    // Wrapping a JCache Cache with SentryJCacheWrapper creates cache.get, cache.put,
    // cache.remove, and cache.flush spans as children of the active transaction.
    demonstrateCacheTracing();

    // Kafka queue tracing with kafka-clients interceptors.
    //
    // This uses the native producer interceptor from sentry-kafka.
    // If no local Kafka broker is available, this block exits quietly.
    demonstrateKafkaTracing();

    // Performance feature
    //
    // Transactions collect execution time of the piece of code that's executed between the start
    // and finish of transaction.
    ITransaction transaction = Sentry.startTransaction("transaction name", "op");
    // Transactions can contain one or more Spans
    ISpan outerSpan = transaction.startChild("child");
    Thread.sleep(100);
    // Spans create a tree structure. Each span can have one ore more spans inside.
    ISpan innerSpan = outerSpan.startChild("jdbc", "select * from product where id = :id");
    innerSpan.setStatus(SpanStatus.OK);
    Thread.sleep(300);
    // Finish the span and mark the end time of the span execution.
    // Note: finishing spans does not sent them to Sentry
    innerSpan.finish();
    // Every SentryEvent reported during the execution of the transaction or a span, will have trace
    // context attached
    Sentry.captureMessage("this message is connected to the outerSpan");
    outerSpan.finish();
    // marks transaction as finished and sends it together with all child spans to Sentry
    transaction.finish();

    // All events that have not been sent yet are being flushed on JVM exit. Events can be also
    // flushed manually:
    // Sentry.close();
  }

  private static void demonstrateCacheTracing() {
    // Create a JCache CacheManager and Cache using standard JSR-107 API
    CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
    MutableConfiguration<String, String> config =
        new MutableConfiguration<String, String>().setTypes(String.class, String.class);
    Cache<String, String> rawCache = cacheManager.createCache("myCache", config);

    // Wrap with SentryJCacheWrapper to enable cache tracing
    Cache<String, String> cache = new SentryJCacheWrapper<>(rawCache);

    // All cache operations inside a transaction produce child spans
    ITransaction transaction = Sentry.startTransaction("cache-demo", "demo");
    try (ISentryLifecycleToken ignored = transaction.makeCurrent()) {
      // cache.put span
      cache.put("greeting", "hello");

      // cache.get span (hit — returns "hello", cache.hit = true)
      cache.get("greeting");

      // cache.get span (miss — returns null, cache.hit = false)
      cache.get("nonexistent");

      // cache.remove span
      cache.remove("greeting");

      // cache.flush span
      cache.clear();
    } finally {
      transaction.finish();
    }

    // Clean up
    cacheManager.destroyCache("myCache");
    cacheManager.close();
  }

  private static void captureMetrics() {
    Sentry.metrics().count("countMetric");
    Sentry.metrics().gauge("gaugeMetric", 5.0);
    Sentry.metrics().distribution("distributionMetric", 7.0);
  }

  private static void demonstrateKafkaTracing() {
    final String topic = "sentry-topic-console-sample";
    final CountDownLatch consumedLatch = new CountDownLatch(1);
    final Thread consumerThread = startKafkaConsumerThread(topic, consumedLatch);

    final Properties producerProperties = new Properties();
    producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    producerProperties.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProperties.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProperties.put(
        ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, SentryKafkaProducerInterceptor.class.getName());

    final ITransaction transaction = Sentry.startTransaction("kafka-demo", "demo");
    try (ISentryLifecycleToken ignored = transaction.makeCurrent()) {
      try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
        Thread.sleep(500);
        producer.send(new ProducerRecord<>(topic, "sentry-kafka sample message")).get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception ignoredException) {
        // local broker may not be available when running the sample
      }

      try {
        consumedLatch.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } finally {
      consumerThread.interrupt();
      try {
        consumerThread.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      transaction.finish();
    }
  }

  private static Thread startKafkaConsumerThread(
      final String topic, final CountDownLatch consumedLatch) {
    final Thread consumerThread =
        new Thread(
            () -> {
              final Properties consumerProperties = new Properties();
              consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
              consumerProperties.put(
                  ConsumerConfig.GROUP_ID_CONFIG, "sentry-console-sample-" + UUID.randomUUID());
              consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
              consumerProperties.put(
                  ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
              consumerProperties.put(
                  ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                  StringDeserializer.class.getName());
              consumerProperties.put(
                  ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                  SentryKafkaConsumerInterceptor.class.getName());

              try (KafkaConsumer<String, String> consumer =
                  new KafkaConsumer<>(consumerProperties)) {
                consumer.subscribe(Collections.singletonList(topic));

                while (!Thread.currentThread().isInterrupted() && consumedLatch.getCount() > 0) {
                  final ConsumerRecords<String, String> records =
                      consumer.poll(Duration.ofMillis(500));
                  if (!records.isEmpty()) {
                    consumedLatch.countDown();
                    break;
                  }
                }
              } catch (Exception ignored) {
                // local broker may not be available when running the sample
              }
            },
            "sentry-kafka-sample-consumer");
    consumerThread.start();
    return consumerThread;
  }

  private static class SomeEventProcessor implements EventProcessor {
    @Override
    public SentryEvent process(SentryEvent event, Hint hint) {
      // Here you can modify the event as you need
      if (event.getLevel() != null && event.getLevel().ordinal() > SentryLevel.INFO.ordinal()) {
        event.addBreadcrumb(new Breadcrumb("Processed by " + SomeEventProcessor.class));
      }

      return event;
    }
  }
}
