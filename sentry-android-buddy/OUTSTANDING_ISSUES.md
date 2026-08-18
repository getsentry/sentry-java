# Sentry Buddy Outstanding Issues

This module is a Hack Week prototype. The current implementation favors a small debug-only surface
over complete tracing control. This document captures the remaining transaction-model questions so
future work can make the tradeoffs deliberately. Accepted behavior-level decisions are summarized in
`DESIGN_DECISIONS.md`; this file focuses on gaps and deferred alternatives.

## Buddy Transaction Model

Buddy starts a root transaction for each recording. The product goal is that developers can open one
Sentry transaction and understand the recorded flow as a coherent unit.

The root transaction is the flow container. It should be sent to Sentry and should hold normal child
spans whenever the SDK can attach those spans naturally. It should not be counted as a child span in
Buddy's summary or protocol events.

Current model:

- Start one root transaction named `Sentry Buddy Recording: <flow-slug>`.
- Force the Buddy root transaction to be sampled so an explicit debug recording produces a Sentry
  transaction artifact even when the app's normal trace sampling rate is lower.
- Bind that transaction to scope while the recording is active.
- Re-bind the Buddy transaction when an Activity resumes, because Android activity tracing may bind
  its own Activity transaction during navigation.
- Temporarily wrap `tracesSampler` so transactions started during an active Buddy recording are
  sampled. Outside active recordings, the wrapper delegates to the previous sampler or returns `null`
  so the SDK can fall back to the app's normal sampling configuration.
- Keep global Buddy tags active during recording so transactions created during the flow can be
  associated with the recording.
- Snapshot child spans from the Buddy transaction into the final Buddy timeline as `span` events.
- Wrap `beforeSendTransaction` to observe already-redacted transactions and copy matching child spans
  into the active Buddy timeline when possible.
- Do not synthesize or mirror spans into the Buddy transaction yet.

This means Buddy is currently a best-effort single-flow transaction. It should capture normal spans
that attach to the current transaction after Buddy has made itself current. It may still miss or only
copy spans created under another transaction before Buddy reasserts itself.

## Why The Root Transaction Is Not Counted As A Span

Sentry transactions are root spans internally, but counting the Buddy root transaction as `1` span made
the UI misleading. A recording with no actual child work showed `Spans: 1`, while the JSON timeline had
no `span` entries.

Buddy now treats span count as child-span count:

- `0` means no child spans were captured.
- `1+` means Buddy captured child spans from the root transaction or copied observed child spans from a
  matching transaction callback.
- The Buddy root transaction remains present in Sentry and remains the flow container.

## `beforeSendTransaction` Role

Buddy wraps the existing app callback instead of replacing it. The wrapper calls the original callback
first. If the app callback returns `null`, Buddy does not record the transaction and returns `null` so
the SDK still drops it. If the app callback returns a mutated/redacted transaction, Buddy records from
that returned transaction.

The callback currently gives Buddy a safe observation point:

- It sees the post-processor, post-redaction transaction payload.
- It can copy child spans from transactions tagged with `sentry.buddy.recording_id`.
- It avoids bypassing app privacy choices.

The callback does not re-parent existing spans. If a span already belongs to an Activity transaction,
Buddy can copy its data into the Buddy recording timeline, but the original Sentry span still belongs
to the Activity transaction unless we later implement a merge or mirroring model.

## Sampling While Recording

Buddy currently changes sampling only while a recording is active. The Buddy root transaction is
created with an explicit sampled decision. Other transactions started during recording go through a
temporary `tracesSampler` wrapper that returns `1.0` while Buddy is active.

This is intended to make debug recordings reliable without changing normal app behavior before or
after the recording. The wrapper is restored when Buddy is uninstalled or reset. If an app's original
sampler or `beforeSendTransaction` later drops/redacts a transaction, Buddy respects that result.

This does not recover transactions that were already sampled out before the recording started, and it
does not capture spans from transactions that finish after Buddy has already finalized the recording.

## Pinned Buddy Trace ID

Another possible correlation layer is to pin the scope propagation context to the Buddy root
transaction's trace ID while a recording is active.

This would not replace the Buddy root transaction. The transaction is still the desired Sentry UI
container for the flow. A pinned trace ID would instead act as an additional join key for telemetry
that does not become a child span of the Buddy transaction.

Potential benefits:

- Events, logs, and metrics captured without an active span could inherit the Buddy trace ID.
- Sentry and Seer lookups could query by `trace:<buddy_trace_id>` in addition to the explicit
  `sentry.buddy.recording_id` tag.
- Transactions that derive from propagation context during the recording may be easier to associate
  with the Buddy flow, even if they remain separate transactions.

Limitations:

- A shared trace ID does not make spans children of the Buddy transaction. Parentage still depends on
  the current transaction/span.
- Activity tracing or incoming trace continuation can still create separate transactions.
- Async work that captured propagation context before the recording started may not use the Buddy trace
  ID.
- Pinning propagation context would need restore logic similar to `tracesSampler` and
  `beforeSendTransaction`.
- Debug-only behavior may be acceptable, but changing propagation context can surprise apps that are
  intentionally continuing an incoming trace.

Status: not implemented. Consider this if tags plus best-effort current transaction are not enough for
Seer/Sentry correlation.

## Breadcrumb Capture Scope

Buddy now has a `beforeBreadcrumb` observation point and records a conservative subset of accepted
breadcrumbs into the flow timeline. The intended long-term scope is still undecided.

Current provisional scope:

- Navigation breadcrumbs.
- HTTP breadcrumbs.
- Breadcrumbs whose category starts with `ui.`.
- Breadcrumbs whose type is `navigation`, `http`, or `user`.

Open decision:

- Should Buddy capture every breadcrumb during a debug recording, or only breadcrumbs likely to help
  reconstruct user flow?
- Should app/custom breadcrumbs be included by default, excluded by default, or controlled through a
  Buddy option?
- Should network breadcrumbs be recorded when a matching network span also exists, or deduplicated?
- Should fragment lifecycle breadcrumbs be promoted to `screen`/`navigation` timeline events instead
  of remaining raw `breadcrumb` events?
- Should click/user breadcrumbs be kept even when labels are weak or potentially noisy?

The answer affects the amount of context sent to the flow-analysis service and the privacy/noise tradeoff
of debug recordings. Keep the current filter conservative until we decide otherwise.

## SDK-Generated Data Sources

Buddy should prefer SDK-generated data before adding Buddy-specific instrumentation. Current status:

Wired:

- Buddy root transaction and child spans that naturally attach to it.
- Matching transaction child spans observed through `beforeSendTransaction` while recording is active.
- Conservative UI/navigation/HTTP/user breadcrumbs observed through `beforeBreadcrumb`.
- Accepted error events observed through `beforeSend` when they have exceptions, `ERROR`, or `FATAL`
  level.

Not yet wired:

- Non-error message events that pass through `beforeSend`.
- SDK logs / `beforeSendLog`.
- Metrics / `beforeSendMetric`.
- Replay IDs, replay segment events, or replay trace correlation beyond the trace IDs already registered
  by the SDK.
- Full event payloads, stack frames, request bodies, screenshots, view hierarchy attachments, or thread
  dumps.
- Feature flag snapshots outside the data already attached to spans/events.

Open decision:

- Should Buddy capture all accepted events during a debug recording, or only error-like events?
- Should logs and metrics be included by default, behind a Buddy option, or omitted until Seer needs
  them?
- Should event payloads stay compact, or should Buddy include richer stack/context data for local-only
  recordings?
- Should screenshots/view hierarchy be captured at stop time, or only when an error event already
  includes them?

## Options We Considered

### 1. Low-Infra Current Transaction Model

Buddy binds its root transaction to scope and lets existing SDK instrumentation attach child spans
naturally.

Pros:

- Minimal new infrastructure.
- Uses normal SDK span creation paths.
- Keeps the Buddy transaction visible in Sentry as the flow container.
- Avoids duplicating spans for the first prototype.

Cons:

- Activity tracing can replace the current transaction during navigation.
- Some spans may still land under Activity transactions or other transactions.
- Fragment, Compose, tab, and click navigation are not automatically represented unless they already
  create Sentry spans.

Status: current approach.

### 2. Synthetic Merge Model

Buddy collects spans from other transactions via `beforeSendTransaction`, stores them, and merges
equivalent span payloads into the Buddy transaction before it is sent.

Pros:

- Sentry UI could show one curated flow transaction.
- Existing Activity/network/db spans could be represented in one place.

Cons:

- Copied spans become synthetic; they are not the original span objects.
- Trace IDs and parent span IDs need rewriting or careful annotation.
- Source transactions may need to be dropped to avoid duplicate UI/noise.
- Finalization timing is harder because transactions may finish after the user stops recording.

Status: intentionally deferred.

### 3. Integration-Level Buddy Mode

Android tracing integrations detect an active Buddy recording and create child spans under the Buddy
transaction instead of creating standalone Activity transactions.

Pros:

- Most semantically correct single-flow transaction.
- Avoids post-hoc span copying.
- Can represent Activity timing spans directly under the Buddy transaction.

Cons:

- Requires changes outside the Buddy module.
- Needs careful interaction with existing tracing, sampling, profiling, and app-start behavior.
- Higher risk for a Hack Week prototype.

Status: possible future direction.

## Remaining Edge Cases

These are still not fully covered by the current low-infra model:

- Fragment-only navigation inside one Activity.
- Compose nested destinations.
- Tab or pager changes that are just UI state.
- Raw button/click interactions.
- Network calls that are not instrumented by Sentry spans.
- Unsampled transactions and spans.
- Spans created before Buddy has re-bound itself after an Activity transition.
- Transactions finishing after the recording has already been finalized.

The next likely improvement is fragment navigation capture, followed by Compose navigation capture.
Those are navigation observability gaps, not transaction-pipeline gaps.
