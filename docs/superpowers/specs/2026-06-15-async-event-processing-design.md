# Async Event Processing Design

## Context

The Java SDK currently invokes `EventProcessor.process(...)` synchronously in `SentryClient` before the relevant `beforeSend*` callback and before enqueueing work for transport or batching. This means custom processor and `beforeSend*` code can run on the caller thread.

We want to introduce an opt-in mode that moves the late mutation/drop phase off the caller thread while keeping the existing synchronous processor phase in place. The first iteration should avoid Android public API compatibility risk from `CompletionStage`/`CompletableFuture`, so async processor methods use the same return shape as the existing synchronous methods.

## Goals

- Add async event processor callbacks for every event type currently supported by `EventProcessor`.
- Keep existing synchronous `process(...)` callbacks in their current positions.
- Add an opt-in `SentryOptions.enableAsyncProcessing` option, defaulting to `false`.
- When async processing is enabled, return from `capture*` after the item is accepted by the async processing queue.
- Isolate potentially slow user callbacks from the shared `SentryOptions.executorService`.
- Make `flush` and `close` wait for accepted async processing work.

## Non-goals

- Do not add `CompletionStage`, `CompletableFuture`, Kotlin coroutines, or other asynchronous return types to the public API.
- Do not change session, check-in, or profile chunk processing; these paths do not use `EventProcessor` today.
- Do not remove or move existing synchronous `process(...)` callbacks.
- Do not guarantee that a returned event ID means the item reaches Sentry.

## Public API

`EventProcessor` gets default `processAsync(...)` overloads that mirror the current `process(...)` overloads:

- `SentryEvent processAsync(SentryEvent event, Hint hint)`
- `SentryTransaction processAsync(SentryTransaction transaction, Hint hint)`
- `SentryReplayEvent processAsync(SentryReplayEvent event, Hint hint)`
- `SentryLogEvent processAsync(SentryLogEvent event)`
- `SentryMetricsEvent processAsync(SentryMetricsEvent event, Hint hint)`

Each default implementation returns the input item unchanged. Returning `null` drops the item, matching existing processor semantics.

`SentryOptions` gets a direct boolean option:

- `isEnableAsyncProcessing()`
- `setEnableAsyncProcessing(boolean enableAsyncProcessing)`

The default is `false`. External configuration support should use the key `enable-async-processing`.

## Runtime Architecture

`SentryClient` owns a dedicated bounded single-thread async processing queue. The queue is separate from `SentryOptions.executorService` so user processor and `beforeSend*` code cannot clog SDK maintenance tasks.

The async processing queue is active only when `enableAsyncProcessing=true`. Its capacity reuses `SentryOptions.maxQueueSize` to avoid adding a second queue-size option in the first iteration.

When `enableAsyncProcessing=false`, `SentryClient` still invokes the new `processAsync(...)` stage, but inline on the caller thread. This keeps the processing pipeline shape consistent and avoids conditional logic around whether async processor callbacks are invoked.

## Telemetry Processor Compatibility

The async processing queue is a callback execution stage, not a delivery buffer or telemetry scheduler. It owns only running `processAsync(...)`, running the matching `beforeSend*` callback, and forwarding accepted items to the existing downstream delivery layer.

Future telemetry processor integration should keep batching, prioritization, overflow policy, trace-aware span bucketing, and offline/cache behavior in the telemetry processor. Async processing should happen before telemetry buffer/scheduler ingestion, and future work may replace or reshape this queue to avoid keeping both a generic FIFO callback queue and category-aware telemetry buffers.

The first iteration uses one bounded FIFO queue because the feature is opt-in. This can cause priority inversion under mixed high-volume traffic, for example logs or metrics filling the callback queue before higher-priority errors reach a future scheduler. Queue overflow at this stage is recorded as `queue_overflow`; future telemetry buffers should use a distinct `buffer_overflow` reason.

`flush(timeoutMillis)` and `close()` must treat async processing and downstream queues as one pipeline and use a shared deadline instead of giving every layer the full timeout independently.

## Processing Pipeline

For each supported capture path, the order is:

1. Existing early filtering and scope work.
2. Existing scoped synchronous `process(...)`, where applicable.
3. Existing global synchronous `process(...)`.
4. New scoped `processAsync(...)`, where applicable.
5. New global `processAsync(...)`.
6. Matching `beforeSend*` callback.
7. Existing send, log batch, or metrics batch queue.

Supported paths:

- Error events: async processors run before `beforeSend`.
- Feedback events: async processors run before `beforeSendFeedback`.
- Transactions: async processors run before `beforeSendTransaction`.
- Replay events: async processors run before `beforeSendReplay`.
- Logs: async processors run before logs `beforeSend`.
- Metrics: async processors run before metrics `beforeSend`.

Sessions, check-ins, and profile chunks are unchanged.

## Captures That Always Stay Synchronous

Some captures coordinate across threads and must not be deferred, even when `enableAsyncProcessing=true`. These run the late processing stage inline on the caller thread:

- Hints implementing `DiskFlushNotification` (uncaught exceptions, ANRv2, tombstones). The caller blocks until the transport reports the event flushed to disk before letting the process die. Queueing would put the fatal event behind everything already enqueued, so the caller's flush timeout can expire before it is ever sent.
- Hints implementing `Backfillable` or `Cached` (events replayed from the outbox or cache). The hint carries retry/delete state that the caller reads the moment capture returns, and the envelope file is deleted based on it.
- Hints implementing `TransactionEnd` (ANR). The capture force-finishes the running transaction so it is persisted before the process dies; deferring that races the process going away.

## Scope Snapshotting

The scope handed to `capture*` is a live `CombinedScopeView` over the caller's thread-local scope. When work is deferred, the scope is cloned at submit time so the async stage sees the state as of the capture call. Without this, a caller that pushes or pops scopes, changes tags, or starts a new transaction before the async stage runs would have that unrelated state attributed to the event.

## Capture Return Behavior

When `enableAsyncProcessing=true`, ID-returning capture methods enqueue the late processing task and return immediately if enqueue succeeds. They return the event, transaction, replay, or feedback ID even though the item may later be dropped by async processors, `beforeSend*`, sampling, rate limiting, transport failures, or downstream queue overflow.

If the async processing queue is full, enqueue fails immediately. The SDK drops the item, records `DiscardReason.QUEUE_OVERFLOW`, and returns `SentryId.EMPTY_ID` for ID-returning capture methods.

Log and metrics capture methods do not return an ID. If the async processing queue is full, they drop the item and record the appropriate queue-overflow client report.

## Error Handling and Client Reports

`processAsync(...)` uses the same error policy as `process(...)`:

- Exceptions from processors are logged.
- Processing continues with the current item.
- Returning `null` drops the item.
- Drops are recorded with `DiscardReason.EVENT_PROCESSOR` and the existing data category for the item type.

`beforeSend*` keeps its current behavior. Exceptions drop the item for PII safety and record `DiscardReason.BEFORE_SEND` where applicable.

Transaction accounting must remain consistent:

- Dropping a transaction records the transaction and all spans.
- Removing spans from a transaction in an async processor or `beforeSendTransaction` records the dropped span count.

## Flush and Close

`SentryClient.flush(timeoutMillis)` waits for the async processing queue to become idle and then waits for downstream log, metrics, and transport queues.

`SentryClient.close()` quiesces the async processing queue *before* draining downstream, so no task can hand an envelope to a transport that is already closing. Work still queued when the shutdown timeout expires is discarded and recorded as `queue_overflow` for its data category rather than being lost silently. Closing remains bounded by existing shutdown and flush timeout behavior, with the async drain and the downstream flush sharing one deadline.

## Testing Plan

- Verify `EventProcessor.processAsync(...)` default methods return input items unchanged.
- Verify `SentryOptions.enableAsyncProcessing` defaults to `false` and can be set directly and through external options.
- Verify inline mode invokes `processAsync(...)` on the caller thread before the matching `beforeSend*` callback.
- Verify async-enabled mode returns before `processAsync(...)` and `beforeSend*` finish.
- Verify ordering: scoped sync processor, global sync processor, scoped async processor, global async processor, `beforeSend*`.
- Verify async queue overflow drops the item, records `queue_overflow`, and returns `SentryId.EMPTY_ID` where applicable.
- Verify `flush` waits for accepted async processing work.
- Verify processor exceptions are logged and do not drop the item.
- Verify `processAsync(...)` returning `null` records `event_processor` drops for events, feedback, transactions and spans, replay, logs, and metrics.
- Verify `beforeSendTransaction` and async transaction processors preserve existing span-loss accounting.
- Verify crash, cached, and backfillable hints bypass the queue and are sent on the caller thread.
- Verify `close` stops accepting new async work before draining downstream, and records `queue_overflow` for work it could not drain.
- Verify the async stage sees the scope as of the capture call, not later caller mutations.
- Verify submitting does not block behind a draining `close`.
