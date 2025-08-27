---
alwaysApply: false
description: Java SDK Offline behaviour
---
# Java SDK Offline behaviour

By default offline caching is enabled for Android but disabled for JVM.
It can be enabled by setting SentryOptions.cacheDirPath.

For Android, AndroidEnvelopeCache is used. For JVM, if cache path has been configured, EnvelopeCache will be used.

Any error, event, transaction, profile, replay etc. is turned into an envelope and then sent into ITransport.send.
The default implementation is AsyncHttpTransport.

If an envelope is dropped due to rate limit and has previously been cached (Cached hint) it will be discarded from the IEnvelopeCache.

AsyncHttpTransport.send will enqueue an AsyncHttpTransport.EnvelopeSender task onto an executor.

Any envelope that doesn't have the Cached hint will be stored in IEnvelopeCache by the EventSender task. Previously cached envelopes (Cached hint) will have a noop cache passed to AsyncHttpTransport.EnvelopeSender and thus not cache again. It is also possible cache is disabled in general.

An envelope being sent directly from SDK API like Sentry.captureException will not have the Retryable hint.

In case the SDK is offline, it'll mark the envelope to be retried if it has the Retryable hint.
If the envelope is not retryable and hasn't been sent to offline cache, it's recorded as lost in a client report.

In case the envelope can't be sent due to an error or network connection problems it'll be marked for retry if it has the Retryable hint.
If it's not retryable and hasn't been cached, it's recorded as lost in a client report.

In case the envelope is sent successfully, it'll be discarded from cache.

The SDK has multiple mechanisms to deal with envelopes on disk.
- OutboxSender: Sends events coming from other SDKs like NDK that wrote them to disk.
- io.sentry.EnvelopeSender: This is the offline cache.

Both of these are set up through an integration (SendCachedEnvelopeIntegration) which is configured to use SendFireAndForgetOutboxSender or SendFireAndForgetEnvelopeSender.

io.sentry.EnvelopeSender is able to pick up files in the cache directory and send them.
It will trigger sending envelopes in cache dir on init and when the connection status changes (e.g. the SDK comes back online, meaning it has Internet connection again).

## When Envelope Files Are Removed From Cache

Envelope files are removed from the cache directory in the following scenarios:

### 1. Successful Send to Sentry Server
When `AsyncHttpTransport` successfully sends an envelope to the Sentry server, it calls `envelopeCache.discard(envelope)` to remove the cached file. This happens in `AsyncHttpTransport.EnvelopeSender.flush()` when `result.isSuccess()` is true.

### 2. Rate Limited Previously Cached Envelopes
If an envelope is dropped due to rate limiting **and** has previously been cached (indicated by the `Cached` hint), it gets discarded immediately via `envelopeCache.discard(envelope)` in `AsyncHttpTransport.send()`.
In this case the discarded envelope is recorded as lost in client reports.

### 3. Offline Cache Processing (EnvelopeSender)
When the SDK processes cached envelope files from disk (via `EnvelopeSender`), files are deleted after processing **unless** they are marked for retry. In `EnvelopeSender.processFile()`, the file is deleted with `safeDelete(file)` if `!retryable.isRetry()`.

### 4. Session File Management
Session-related files (session.json, previous_session.json) are removed during session lifecycle events like session start/end and abnormal exits.

### 5. Cache rotation
If the number of files in the cache directory has reached the configured limit (SentryOptions.maxCacheItems), the oldest file will be deleted to make room.
This happens in `CacheStrategy.rotateCacheIfNeeded`. The deleted envelope will be recorded as lost in client reports.

## Retry Mechanism

**Important**: The SDK does NOT implement a traditional "max retry count" mechanism. Instead:

### Infinite Retry Approach
- **Retryable envelopes**: Stay in cache indefinitely and are retried when conditions improve (network connectivity restored, rate limits expire, etc.)
- **Non-retryable envelopes**: If they fail to send, they're immediately recorded as lost (not cached for retry)

### When Envelopes Are Permanently Lost (Not Due to Retry Limits)

1. **Queue Overflow**: When the transport executor queue is full - recorded as `DiscardReason.QUEUE_OVERFLOW`

2. **Network Errors (Non-Retryable)**: When an envelope isn't marked as retryable and fails due to network issues - recorded as `DiscardReason.NETWORK_ERROR`

3. **Rate Limiting**: When envelope items are dropped due to active rate limits - recorded as `DiscardReason.RATELIMIT_BACKOFF`

4. **Cache Overflow**: When the cache directory has reached maxCacheItems, old files are deleted - recorded as `DiscardReason.CACHE_OVERFLOW`

### Cache Processing Triggers
Cached envelopes are processed when:
- Network connectivity is restored (via connection status observer)
- SDK initialization occurs
- Rate limits expire
- Manual flush operations

### File Deletion Implementation
The actual file deletion is handled by `EnvelopeCache.discard()` which calls `envelopeFile.delete()` and logs errors if deletion fails.
