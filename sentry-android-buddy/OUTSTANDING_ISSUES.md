# Sentry Buddy Outstanding Issues

This module is a Hack Week prototype. The current implementation favors a small debug-only surface
over complete tracing control. This document captures the remaining transaction-model questions so
future work can make the tradeoffs deliberately.

## Buddy Transaction Model

Buddy starts a root transaction for each recording. The product goal is that developers can open one
Sentry transaction and understand the recorded flow as a coherent unit.

The root transaction is the flow container. It should be sent to Sentry and should hold normal child
spans whenever the SDK can attach those spans naturally. It should not be counted as a child span in
Buddy's summary or protocol events.

Current model:

- Start one root transaction named `Sentry Buddy Recording: <flow-slug>`.
- Bind that transaction to scope while the recording is active.
- Re-bind the Buddy transaction when an Activity resumes, because Android activity tracing may bind
  its own Activity transaction during navigation.
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
