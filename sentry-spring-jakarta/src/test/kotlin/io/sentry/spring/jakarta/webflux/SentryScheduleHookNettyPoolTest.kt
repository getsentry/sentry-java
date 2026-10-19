package io.sentry.spring.jakarta.webflux

import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.Sentry
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.client.HttpClient
import reactor.netty.http.server.HttpServer
import reactor.netty.resources.ConnectionProvider

class SentryScheduleHookNettyPoolTest {
  private fun depth(scopes: IScopes?): Int = generateSequence(scopes) { it.parentScopes }.count()

  @Test
  fun `reactor netty pool eviction keeps the scopes chain bounded`() {
    Sentry.init { it.dsn = "https://key@localhost/proj" }

    val maxDepth = AtomicInteger()
    val evictionSchedules = AtomicInteger()
    val enoughEvictions = CountDownLatch(1)
    Schedulers.onScheduleHook("probe") { runnable ->
      if (
        runnable.javaClass.name.startsWith(
          "reactor.netty.internal.shaded.reactor.pool.SimpleDequePool"
        )
      ) {
        maxDepth.accumulateAndGet(depth(Sentry.getCurrentScopes()), ::maxOf)
        if (evictionSchedules.incrementAndGet() == 100) {
          enoughEvictions.countDown()
        }
      }
      runnable
    }
    Schedulers.onScheduleHook("sentry", SentryScheduleHook())

    val server =
      HttpServer.create()
        .port(0)
        .handle { _, response -> response.sendString(Mono.just("ok")) }
        .bindNow()
    val pool =
      ConnectionProvider.builder("scope-chain-regression")
        .maxIdleTime(Duration.ofMillis(1))
        .evictInBackground(Duration.ofMillis(1))
        .build()

    try {
      HttpClient.create(pool)
        .get()
        .uri("http://localhost:${server.port()}/")
        .responseContent()
        .aggregate()
        .asString()
        .block()

      assertThat(enoughEvictions.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(maxDepth.get()).isLessThan(10)
    } finally {
      Schedulers.resetOnScheduleHook("sentry")
      Schedulers.resetOnScheduleHook("probe")
      pool.dispose()
      server.disposeNow()
      Sentry.close()
    }
  }
}
