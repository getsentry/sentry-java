package io.sentry.samples.spring.boot.jakarta;

import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncService {

  private final ApiService apiService;

  public AsyncService(ApiService apiService) {
    this.apiService = apiService;
  }

  @Async
  public void doAsync() {
    TransactionContext transactionContext =
        //      Sentry.continueTrace(
        //        "b9118105af4a2d42b4124532cd1065aa-636cffc8f94fbbbb-1",
        //        Arrays.asList(
        //
        // "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=0.654,sentry-trace_id=b9118105af4a2d42b4124532cd1065aa,sentry-transaction=continued-transaction-from-baggage"));
        new TransactionContext("async-transaction-no-headers", "async-op-no-headers");
    //    transactionContext.setName("async-transaction");
    //    transactionContext.setOperation("async-op");
    TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setBindToScope(true);
    ITransaction iTransaction = Sentry.startTransaction(transactionContext, transactionOptions);
    //    ITransaction iTransaction =
    //        Sentry.startTransaction("async-transaction", "async-op", transactionOptions);

    System.out.println("running transaction ...");
    try {
      Thread.sleep(1000);
      apiService.apiRequest("def-async");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      System.out.println("transaction done");
      iTransaction.finish(SpanStatus.UNIMPLEMENTED);
    }
  }
}
