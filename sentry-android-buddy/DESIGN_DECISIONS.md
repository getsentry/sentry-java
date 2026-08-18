# Sentry Buddy Design Decisions

This document records significant decisions for the Sentry Buddy prototype. Keep it updated when the
module changes behavior. Use `OUTSTANDING_ISSUES.md` for unresolved tradeoffs, known gaps, and future
directions.

## Debug-Only Developer Tool

Buddy is designed to be installed by applications in debug builds, usually with `debugImplementation`,
and enabled explicitly with `SentryBuddy.install(application)`.

Decision:

- Keep Buddy outside the BOM.
- Do not make Buddy part of the normal production Android SDK path.
- Prefer behavior that is useful and predictable for local debugging over production-safe defaults.

Rationale:

- Buddy changes tracing and sampling behavior during recordings.
- Developers explicitly start a recording and expect the resulting flow artifact to exist.
- Keeping the module debug-only reduces the compatibility and overhead risk of those choices.

## One Buddy Root Transaction Per Recording

Buddy creates one root transaction for each recording. The transaction name is
`Sentry Buddy Recording: <flow-slug>`.

Decision:

- Treat the Buddy root transaction as the flow container in Sentry.
- Bind the Buddy transaction to scope while recording.
- Re-bind the Buddy transaction when an Activity resumes, because Android activity tracing can bind
  Activity transactions during navigation.
- Do not count the root transaction as a child span.

Rationale:

- The intended product experience is that a developer can open one transaction and understand the
  recorded flow.
- A Sentry transaction is internally a root span, but showing `Spans: 1` for a recording with no child
  work was misleading.
- Re-binding is a low-infrastructure way to let existing SDK instrumentation attach child spans to the
  Buddy flow after Activity transitions.

Open questions and alternatives are tracked in `OUTSTANDING_ISSUES.md`.

## Sampling During Recordings

Buddy changes sampling only while a recording is active.

Decision:

- Create the Buddy root transaction with an explicit sampled decision.
- Temporarily wrap `tracesSampler` during Buddy install.
- Return `1.0` from that wrapper while a recording is active.
- Delegate to the previous sampler when no recording is active.
- Return `null` when no recording is active and no previous sampler exists, so the SDK can use normal
  fallback sampling behavior.
- Restore the previous sampler when Buddy is uninstalled or reset.

Rationale:

- An explicit debug recording should produce a Sentry transaction artifact.
- Sampling transactions started during the recording makes it more likely that Buddy can observe spans
  that still attach to non-Buddy transactions.
- Outside active recordings, Buddy should not change application tracing behavior.

Limitations:

- This does not recover transactions sampled out before the recording started.
- This does not include transactions that finish after Buddy has already finalized the recording.
- App `beforeSendTransaction` decisions still win; if the app drops a transaction, Buddy does not keep
  a private copy.

## Use Existing SDK Spans First

Buddy should rely on spans that existing SDK instrumentation already creates before adding new
Buddy-specific instrumentation.

Decision:

- Let normal SDK instrumentation attach child spans to the current Buddy transaction when possible.
- Snapshot child spans from the Buddy transaction into the final Buddy recording timeline as `span`
  events.
- Observe post-redaction transactions through `beforeSendTransaction` and copy matching child spans
  into the active Buddy recording timeline when possible.
- Do not synthesize or mirror observed spans into the Buddy transaction yet.

Rationale:

- Reusing existing spans avoids inventing a parallel instrumentation model.
- Recording from the transaction returned by the app's `beforeSendTransaction` respects app redaction
  and drop decisions.
- Avoiding span mirroring prevents duplicate or synthetic telemetry in the first prototype.

Deferred alternatives:

- Merge spans from multiple transactions into one synthetic Buddy transaction.
- Add integration-level Buddy modes that create Activity or navigation child spans under the Buddy
  transaction directly.
- Add explicit Buddy-generated spans for UI interactions, fragment navigation, or Compose navigation.

## Capture Useful Breadcrumbs

Buddy records a conservative subset of breadcrumbs during an active recording.

Decision:

- Wrap `beforeBreadcrumb` during Buddy install and restore the previous callback on uninstall/reset.
- Call the app's original callback first.
- Record only the returned breadcrumb; if the app callback drops it, Buddy does not keep a private copy.
- Capture navigation, HTTP, `ui.*`, `navigation`, `http`, and `user` breadcrumbs for now.
- Store breadcrumbs as `BuddyTimelineItem.Type.BREADCRUMB` rather than promoting them into higher-level
  event types yet.

Rationale:

- Fragment lifecycle, navigation, user interaction, and HTTP integrations already produce useful
  breadcrumbs.
- Recording accepted breadcrumbs lets Buddy reuse existing SDK instrumentation without adding parallel
  navigation/click instrumentation immediately.
- A conservative filter avoids dumping every custom breadcrumb into the flow-analysis payload before we
  decide the privacy/noise tradeoff.

Open questions about the final breadcrumb scope are tracked in `OUTSTANDING_ISSUES.md`.

## Capture Accepted Error Events

Buddy records accepted Sentry error events during an active recording.

Decision:

- Wrap `beforeSend` during Buddy install and restore the previous callback on uninstall/reset.
- Call the app's original callback first.
- Record only the returned event; if the app callback drops it, Buddy does not keep a private copy.
- Capture events with exceptions, `ERROR` level, or `FATAL` level.
- Store accepted events as `BuddyTimelineItem.Type.EVENT` with compact metadata rather than serializing
  the full event payload.

Rationale:

- Errors during a recorded flow are high-value context for Seer and for the local flow summary.
- Recording after `beforeSend` respects app filtering and redaction.
- Keeping the payload compact avoids sending stack frames, request bodies, or full event context through
  Buddy before we decide the final privacy boundary.

## Ktor Flow Analysis Protocol Shape

Buddy's local protocol model mirrors the prototype Ktor flow-analysis API.

Decision:

- Use `FlowAnalysisRequest` with `flow_id`, `trace_ids`, `start_time_ms`, `end_time_ms`, `dsn`,
  `user_annotation`, `sdk_version`, and `events`.
- Use `FlowAnalysisEvent(type, time_ms, data)` as the open event shape.
- Model submit, poll, and resolve operations as:
  - `POST /v1/flow-analysis`
  - `GET /v1/flow-analysis/{flowId}`
  - `POST /v1/flow-analysis/{flowId}/recommendations/{id}/resolve`
- Use `AnalysisStatus.PROCESSING` for the initial status, matching the Ktor enum and decisions even
  though the endpoint table mentions `PENDING`.

Rationale:

- The protocol stays close to the mock Ktor service while allowing Buddy to move quickly.
- `FlowAnalysisEvent.data` remains open so richer events can be added without changing the protocol
  model for every prototype iteration.

## Assemble Recording JSON From Buddy-Owned State

This description reflects the implementation at commit `788f4b028aff53c7d9dc763f23b6436eb423fa05`
as observed on `2026-08-18 09:25:48 CEST`.

Buddy does not build the final recording JSON by scraping a completed Sentry event, replay artifact,
or transport payload after the flow finishes. Instead, Buddy owns a small in-memory recording session,
collects a narrow set of signals while that session is active, materializes one
`BuddyFlowRecording` at stop time, and only then serializes that object to JSON.

Decision:

- Start each recording by creating Buddy-owned session state with a recording ID, flow intent, start
  timestamps, correlation tags, and one Buddy root transaction.
- Populate the final artifact from distinct sources:
  - developer-authored flow intent
  - Buddy-generated recording metadata
  - runtime app and device metadata
  - a normalized timeline accumulated during the recording window
  - Sentry correlation fields captured from the Buddy transaction and tags
- Treat the timeline as the primary source of truth for what happened during the flow.
- Derive summary fields such as counts and duration from the finalized timeline rather than storing a
  separate parallel summary stream during capture.
- Keep JSON serialization as a final formatting step over the already-shaped `BuddyFlowRecording`
  object.

Rationale:

- A Buddy-owned recording model keeps the artifact intentional and explainable instead of coupling it
  to whichever Sentry payload happened to exist at the end.
- Separating flow intent, metadata, timeline, and Sentry correlation makes it easier to evolve the
  recording format without turning it into a raw telemetry dump.
- Deriving summary from the finalized timeline reduces disagreement between the human-readable rollup
  and the recorded event sequence.
- Restricting serialization to the final step keeps the schema stable and the capture pipeline easier
  to reason about.

Timeline construction options considered:

| Option | Major pros | Major cons |
|---|---|---|
| Build the timeline as signals are produced | Simple, local, easy to test, immediately useful for overlay and local JSON | Can normalize too early, miss late signals, and make ordering/deduplication harder |
| Buffer raw observations and normalize at stop | Preserves flexibility, allows better sorting, deduplication, and schema evolution | Adds a normalization layer and requires separate handling for live UI feedback |
| Reconstruct the timeline after the fact from Sentry artifacts | Reuses backend telemetry and can include richer Sentry data later | Depends on network, sampling, redaction, ingestion, and correlation completeness |
| Use a live flow skeleton with post-stop enrichment | Keeps developer intent and screens as the backbone while allowing richer telemetry merge | Requires clear merge, provenance, conflict, and duplicate-handling rules |
| Persist a local append-only journal | Can survive process death and capture pre-crash context | Adds storage, cleanup, corruption, and privacy responsibilities |
| Let the flow-analysis service own timeline construction | Keeps SDK thinner and lets server-side analysis evolve faster | Makes local JSON less useful and depends on service availability and behavior |
| Treat developer steps as the timeline and attach telemetry as context | Produces a cleaner flow narrative and keeps telemetry from overwhelming intent | Requires matching telemetry to steps/screens and can hide standalone chronological detail |

The current prototype mostly builds the normalized timeline as signals are produced, then sorts and
finalizes it at stop time. A likely next refinement is the hybrid model: record a live skeleton from
developer steps and screen transitions, collect Sentry telemetry as observations, and normalize or
attach those observations after stop. That keeps developer-authored flow markers canonical while
allowing spans, breadcrumbs, events, and later backend data to enrich the recording.

What this means in practice:

- Buddy records explicit developer steps and screen transitions directly.
- Buddy also observes a conservative subset of accepted Sentry breadcrumbs, accepted error events,
  and sampled transactions/spans during the active recording window.
- On stop, Buddy sorts and finalizes those accumulated items, computes summary fields, snapshots app
  and device metadata, attaches Sentry trace correlation, and exports one versioned JSON document.

## Documentation Maintenance

Decision:

- Update this document when a behavior-level design decision changes.
- Update `OUTSTANDING_ISSUES.md` when the change affects known gaps, deferred alternatives, or future
  work.

Rationale:

- Buddy is moving quickly, and the transaction/sampling model has subtle consequences.
- Keeping decisions separate from open issues makes future iteration easier to review.
