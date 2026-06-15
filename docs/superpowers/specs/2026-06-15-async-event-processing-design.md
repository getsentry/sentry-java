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

`SentryClient.close()` drains async processing during shutdown and then closes downstream processors and transport. Closing remains bounded by existing shutdown and flush timeout behavior.

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
