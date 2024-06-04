package io.sentry.samples.spring.boot.jakarta;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import io.sentry.BaggageHeader;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

@Service
public class JmsService {
  private static final String QUEUE_NAME = "jmsMailbox";
  public JmsTemplate jmsTemplate;

  public JmsService(JmsTemplate jmsTemplate) {
    this.jmsTemplate = jmsTemplate;
  }

  public void sendMessage(String queueMessage) {
    ISpan span = Sentry.getSpan().startChild("queue.publish");
    BaggageHeader baggage = Sentry.getBaggage();
    SentryTraceHeader trace = Sentry.getTraceparent();

    jmsTemplate.convertAndSend(QUEUE_NAME, queueMessage, message -> {
      message.setJMSMessageID("ID:" + UUID.randomUUID());

      if(trace != null) {
        message.setStringProperty(trace.getPropertyName(), trace.getValue());
      }

      if(baggage != null) {
        message.setStringProperty(baggage.getPropertyName(), baggage.getValue());
      }

      if(message.getJMSMessageID() != null) {
        span.setData("messaging.message.id", message.getJMSMessageID());
      }

      span.setData("messaging.destination.name", QUEUE_NAME);
      span.setData("messaging.message.body.size", message.getBody(String.class).length());


      return message;
    });
    span.finish();
  }

  @JmsListener(destination = QUEUE_NAME)
  public void receiveMessage(Message emailMessage) throws JMSException {

    String traceHeader = emailMessage.getStringProperty(SentryTraceHeader.SENTRY_TRACE_HEADER.replace("-", "_"));
    String baggageHeader = emailMessage.getStringProperty(BaggageHeader.BAGGAGE_HEADER.replace("-", "_"));

    TransactionContext context = Sentry.continueTrace(traceHeader, List.of(baggageHeader));
    context.setOperation("function");
    context.setName("queue_consumer_transaction");
    ISpan transaction = Sentry.startTransaction(context);
    ISpan span = transaction.startChild("queue.process");

    Long latency = System.currentTimeMillis() - emailMessage.getJMSTimestamp();

    String payload = emailMessage.getBody(String.class);

    span.setData("messaging.message.id", emailMessage.getJMSMessageID());
    span.setData("messaging.destination.name", QUEUE_NAME);
    span.setData("messaging.message.body.size", payload.length());
    span.setData("messaging.message.receive.latency", latency);
    span.setData("messaging.message.retry.count", 0);


    span.finish(SpanStatus.OK);
    transaction.finish(SpanStatus.OK);
  }
}
