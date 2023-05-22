package io.sentry.samples.spring.boot;

import io.sentry.AsyncHttpTransportFactory;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.ITransportFactory;
import io.sentry.MyRequestDetailsResolver;
import io.sentry.RequestDetails;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryTransaction;
import io.sentry.transport.ITransport;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class SentryDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(SentryDemoApplication.class, args);
  }

  @Bean
  WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }

  // could instead simply use
  //    options.addEventProcessor(...);
  //    options.setTransportFactory(...);
  @Bean
  EventProcessor myEventProcessor() {
    return new EventProcessor() {
      @Override
      public @Nullable SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
        addHint(event, hint);
        return event;
      }

      @Override
      public @Nullable SentryTransaction process(
          @NotNull SentryTransaction transaction, @NotNull Hint hint) {
        addHint(transaction, hint);
        return transaction;
      }

      private void addHint(@NotNull SentryBaseEvent event, @NotNull Hint hint) {
        Object extra = event.getExtra("my-project-extra-key");
        // using "sentry:" prefix so hint survives into transport
        hint.set("sentry:not-really-sentry_my-project", extra);
      }
    };
  }

  @Bean
  ITransportFactory transportFactory() {
    return new ITransportFactory() {

      @Override
      public @NotNull ITransport create(
          @NotNull SentryOptions options, @NotNull RequestDetails requestDetails) {
        AsyncHttpTransportFactory asyncFactory = new AsyncHttpTransportFactory();
        ITransport transportA =
            asyncFactory.create(
                options,
                MyRequestDetailsResolver.resolve(
                    "http://0980e31ce2bf48e4926f305f175c4201@localhost:8000/1",
                    options.getSentryClientName()));
        ITransport fallbackTransport =
            asyncFactory.create(
                options,
                MyRequestDetailsResolver.resolve(options.getDsn(), options.getSentryClientName()));
        return new MultiDsnTransport(transportA, fallbackTransport);
      }
    };
  }

  public static class MultiDsnTransport implements ITransport {

    private ITransport transportA;
    private ITransport fallbackTransport;

    public MultiDsnTransport(
        final @NotNull ITransport transportA, final @NotNull ITransport fallbackTransport) {
      this.transportA = transportA;
      this.fallbackTransport = fallbackTransport;
    }

    @Override
    public void send(@NotNull SentryEnvelope envelope, @NotNull Hint hint) throws IOException {
      Object project = hint.get("sentry:not-really-sentry_my-project");
      if (project != null && "project-a".equals(project)) {
        transportA.send(envelope, hint);
      } else {
        fallbackTransport.send(envelope, hint);
      }
    }

    @Override
    public void flush(long timeoutMillis) {
      transportA.flush(timeoutMillis);
      fallbackTransport.flush(timeoutMillis);
    }

    @Override
    public void close() throws IOException {
      transportA.close();
      fallbackTransport.close();
    }
  }
}
