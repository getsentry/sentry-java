package io.sentry.android.core

import io.sentry.DataCategory
import io.sentry.IContinuousProfiler
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ProfileLifecycle
import io.sentry.SentryLevel
import io.sentry.TracesSampler
import io.sentry.protocol.SentryId
import io.sentry.test.DeferredExecutorService
import io.sentry.transport.RateLimiter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Shared dependencies for profiler test cases. Each test class creates one from its own fixture.
 */
class ProfilerMocks(
  val executor: DeferredExecutorService,
  val tracesSampler: TracesSampler,
  val logger: ILogger,
  val scopes: IScopes,
)

// -- Shared test cases as extension functions on IContinuousProfiler --

fun IContinuousProfiler.testIsRunningReflectsStatus(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  stopProfiler(ProfileLifecycle.MANUAL)
  mocks.executor.runAll()
  assertFalse(isRunning)
}

fun IContinuousProfiler.testStopProfilerStopsAfterChunkFinished(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  stopProfiler(ProfileLifecycle.MANUAL)
  assertTrue(isRunning)
  assertNotEquals(SentryId.EMPTY_ID, profilerId)
  assertNotEquals(SentryId.EMPTY_ID, chunkId)
  mocks.executor.runAll()
  assertFalse(isRunning)
  assertEquals(SentryId.EMPTY_ID, profilerId)
  assertEquals(SentryId.EMPTY_ID, chunkId)
}

fun IContinuousProfiler.testMultipleStartsAcceptedInTraceMode(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.TRACE, mocks.tracesSampler)
  assertTrue(isRunning)
  startProfiler(ProfileLifecycle.TRACE, mocks.tracesSampler)
  assertTrue(isRunning)

  stopProfiler(ProfileLifecycle.TRACE)
  mocks.executor.runAll()
  assertTrue(isRunning)

  stopProfiler(ProfileLifecycle.TRACE)
  mocks.executor.runAll()
  assertFalse(isRunning)
}

fun IContinuousProfiler.testLogsWarningIfNotSampled(mocks: ProfilerMocks) {
  whenever(mocks.tracesSampler.sampleSessionProfile(any())).thenReturn(false)
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertFalse(isRunning)
  verify(mocks.logger)
    .log(eq(SentryLevel.DEBUG), eq("Profiler was not started due to sampling decision."))
}

fun IContinuousProfiler.testEvaluatesSessionSampleRateOnlyOnce(mocks: ProfilerMocks) {
  verify(mocks.tracesSampler, never()).sampleSessionProfile(any())
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  verify(mocks.tracesSampler, times(1)).sampleSessionProfile(any())
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  verify(mocks.tracesSampler, times(1)).sampleSessionProfile(any())
}

fun IContinuousProfiler.testReevaluateSamplingOnNextStart(mocks: ProfilerMocks) {
  verify(mocks.tracesSampler, never()).sampleSessionProfile(any())
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  verify(mocks.tracesSampler, times(1)).sampleSessionProfile(any())
  reevaluateSampling()
  verify(mocks.tracesSampler, times(1)).sampleSessionProfile(any())
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  verify(mocks.tracesSampler, times(2)).sampleSessionProfile(any())
}

fun IContinuousProfiler.testStopsAndRestartsForEachChunk(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  val oldChunkId = chunkId

  mocks.executor.runAll()
  verify(mocks.logger).log(eq(SentryLevel.DEBUG), eq("Profile chunk finished. Starting a new one."))
  assertTrue(isRunning)

  mocks.executor.runAll()
  verify(mocks.logger, times(2))
    .log(eq(SentryLevel.DEBUG), eq("Profile chunk finished. Starting a new one."))
  assertTrue(isRunning)
  assertNotEquals(oldChunkId, chunkId)
}

fun IContinuousProfiler.testSendsChunkOnRestart(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  mocks.executor.runAll()
  verify(mocks.scopes, never()).captureProfileChunk(any())
  mocks.executor.runAll()
  verify(mocks.scopes).captureProfileChunk(any())
}

fun IContinuousProfiler.testSendsChunkOnStop(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  mocks.executor.runAll()
  verify(mocks.scopes, never()).captureProfileChunk(any())
  stopProfiler(ProfileLifecycle.MANUAL)
  mocks.executor.runAll()
  verify(mocks.scopes).captureProfileChunk(any())
}

fun IContinuousProfiler.testCloseWithoutTerminatingStopsAfterChunk(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  startProfiler(ProfileLifecycle.TRACE, mocks.tracesSampler)
  assertTrue(isRunning)
  close(false)
  assertTrue(isRunning)
  mocks.executor.runAll()
  assertFalse(isRunning)
}

fun IContinuousProfiler.testDoesNotSendChunksAfterClose(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  close(true)
  mocks.executor.runAll()
  verify(mocks.scopes, never()).captureProfileChunk(any())
}

fun IContinuousProfiler.testStopsWhenRateLimited(mocks: ProfilerMocks) {
  val rateLimiter = mock<RateLimiter>()
  whenever(rateLimiter.isActiveForCategory(DataCategory.ProfileChunkUi)).thenReturn(true)
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  (this as RateLimiter.IRateLimitObserver).onRateLimitChanged(rateLimiter)
  assertFalse(isRunning)
  assertEquals(SentryId.EMPTY_ID, profilerId)
  assertEquals(SentryId.EMPTY_ID, chunkId)
  verify(mocks.logger).log(eq(SentryLevel.WARNING), eq("SDK is rate limited. Stopping profiler."))
}

fun IContinuousProfiler.testDoesNotStartWhenRateLimited(mocks: ProfilerMocks) {
  val rateLimiter = mock<RateLimiter>()
  whenever(rateLimiter.isActiveForCategory(DataCategory.ProfileChunkUi)).thenReturn(true)
  whenever(mocks.scopes.rateLimiter).thenReturn(rateLimiter)
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertFalse(isRunning)
  assertEquals(SentryId.EMPTY_ID, profilerId)
  assertEquals(SentryId.EMPTY_ID, chunkId)
  verify(mocks.logger).log(eq(SentryLevel.WARNING), eq("SDK is rate limited. Stopping profiler."))
}

fun IContinuousProfiler.testDoesNotStartWhenOffline(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertFalse(isRunning)
  assertEquals(SentryId.EMPTY_ID, profilerId)
  assertEquals(SentryId.EMPTY_ID, chunkId)
  verify(mocks.logger).log(eq(SentryLevel.WARNING), eq("Device is offline. Stopping profiler."))
}

fun IContinuousProfiler.testCanBeStartedAgainAfterStopCycle(mocks: ProfilerMocks) {
  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  stopProfiler(ProfileLifecycle.MANUAL)
  mocks.executor.runAll()
  assertFalse(isRunning)

  startProfiler(ProfileLifecycle.MANUAL, mocks.tracesSampler)
  assertTrue(isRunning)
  mocks.executor.runAll()
  assertTrue(isRunning, "shouldStop must be reset on start")
}
