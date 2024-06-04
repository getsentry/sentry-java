package io.sentry.samples.spring.boot.jakarta;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.sentry.BaggageHeader;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;

@Service
public class AmqpService {

  private final AmqpTemplate amqpTemplate;

  public AmqpService(AmqpTemplate amqpTemplate) {
    this.amqpTemplate = amqpTemplate;
  }

  private static final String QUEUE_NAME = "rabbitMailbox";

  public void sendMessage(String queueMessage) {
    ISpan span = Sentry.getSpan().startChild("queue.publish");
    BaggageHeader baggage = Sentry.getBaggage();
    SentryTraceHeader trace = Sentry.getTraceparent();

    amqpTemplate.convertAndSend("", QUEUE_NAME, queueMessage, message -> {
      message.getMessageProperties().setMessageId(UUID.randomUUID().toString());

      if(trace != null) {
        message.getMessageProperties().setHeader(SentryTraceHeader.SENTRY_TRACE_HEADER, trace.getValue());
      }

      if(baggage != null) {
        message.getMessageProperties().setHeader(BaggageHeader.BAGGAGE_HEADER, baggage.getValue());
      }

      if(message.getMessageProperties().getMessageId() != null) {
        span.setData("messaging.message.id", message.getMessageProperties().getMessageId());
      }

      message.getMessageProperties().setTimestamp(new Date());

      span.setData("messaging.destination.name", QUEUE_NAME);
      span.setData("messaging.message.body.size", message.getBody().length);

      return message;
    });
  }

  @RabbitListener(queues = QUEUE_NAME)
  public void process(Message message) {
    String traceHeader = message.getMessageProperties().getHeader(SentryTraceHeader.SENTRY_TRACE_HEADER);
    String baggageHeader = message.getMessageProperties().getHeader(BaggageHeader.BAGGAGE_HEADER);

    TransactionContext context = Sentry.continueTrace(traceHeader, List.of(baggageHeader));
    context.setOperation("function");
    context.setName("queue_consumer_transaction");
    ISpan transaction = Sentry.startTransaction(context);
    ISpan span = transaction.startChild("queue.process");

    long latency = 0L;
    if(message.getMessageProperties().getTimestamp() != null) {
      latency = System.currentTimeMillis() - message.getMessageProperties().getTimestamp().getTime();
    }


    span.setData("messaging.message.id", message.getMessageProperties().getMessageId());
    span.setData("messaging.destination.name", QUEUE_NAME);
    span.setData("messaging.message.body.size", message.getBody().length);
    span.setData("messaging.message.receive.latency", latency);
    span.setData("messaging.message.retry.count", 0);

    String payload = new String(message.getBody());

    span.finish(SpanStatus.OK);
    transaction.finish(SpanStatus.OK);
  }
}
