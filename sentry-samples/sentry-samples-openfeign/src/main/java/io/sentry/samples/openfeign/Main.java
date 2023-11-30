package io.sentry.samples.openfeign;

import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.TransactionOptions;
import io.sentry.openfeign.SentryCapability;
import java.util.List;

public class Main {

  public static void main(String[] args) {
    Sentry.init(
        options -> {
          // NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in
          // your Sentry project/dashboard
          options.setDsn(
              "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563");

          // Performance configuration options
          // Set what percentage of traces should be collected
          options.setTracesSampleRate(1.0); // set 0.5 to send 50% of traces
        });

    TodoApi todoApi =
        Feign.builder()
            .addCapability(
                new SentryCapability(
                    (span, request, response) -> {
                      // attach tag to request with urls ending with /todos
                      if (request.url().endsWith("/todos")) {
                        span.setTag("tag-name", "tag-value");
                      }
                      return span;
                    }))
            .encoder(new GsonEncoder())
            .decoder(new GsonDecoder())
            .target(TodoApi.class, "https://jsonplaceholder.typicode.com/");

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setBindToScope(true);
    final ITransaction transaction =
        Sentry.startTransaction("load-todos2", "console", transactionOptions);
    final List<Todo> all = todoApi.findAll();
    System.out.println("Loaded " + all.size() + " todos");
    System.out.println(todoApi.findById(1L));
    System.out.println(todoApi.findById(2L));
    transaction.finish();
  }

  interface TodoApi {
    @RequestLine("GET /todos")
    List<Todo> findAll();

    @RequestLine("GET /todos/{id}")
    Todo findById(@Param("id") Long id);
  }

  static class Todo {
    private Long id;
    private Long userId;
    private String title;

    public void setId(Long id) {
      this.id = id;
    }

    public void setUserId(Long userId) {
      this.userId = userId;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public Long getId() {
      return id;
    }

    public Long getUserId() {
      return userId;
    }

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return "Todo{" + "id=" + id + ", userId=" + userId + ", title='" + title + '\'' + '}';
    }
  }
}
