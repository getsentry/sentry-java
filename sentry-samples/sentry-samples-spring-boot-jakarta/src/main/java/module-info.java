module sentry.samples.spring.boot.jakarta {
  requires org.jetbrains.annotations;
  requires io.sentry;
  requires io.sentry.spring.jakarta;
  requires io.sentry.spring.boot.jakarta;
  requires io.sentry.quartz;
  requires org.dataloader;
  requires org.quartz;
  requires org.slf4j;
  requires reactor.core;
  requires spring.boot;
  requires spring.boot.autoconfigure;
  requires spring.context;
  requires spring.context.support;
  requires spring.graphql;
  requires spring.jdbc;
  requires spring.security.config;
  requires spring.security.core;
  requires spring.security.crypto;
  requires spring.security.web;
  requires spring.web;
  requires spring.webflux;
}
