# Add versioned recording format and JSON serializer

Status: ready-for-agent
Type: AFK
Blocked by: 01

## Description

Add the public Buddy recording model and deterministic JSON serializer for the first version of the `sentry.mobile_flow_recording` artifact. The schema should represent developer intent, recording metadata, app/device metadata, summary information, normalized timeline items, and Sentry correlation data without depending on UI, backend ingest, or SDK callback interception.

## Acceptance criteria

- [ ] The public recording model represents flow intent, recording metadata, app metadata, device metadata, summary stats, timeline items, and Sentry correlation.
- [ ] The top-level artifact serializes `type = sentry.mobile_flow_recording`, `version = 1`, `platform = android`, and `useCase = onboard_new_flow`.
- [ ] Timeline item types include `recording_started`, `screen`, `step`, `breadcrumb`, and `recording_stopped`.
- [ ] The MVP serializer supports `recording_started`, `screen`, `step`, and `recording_stopped` items; `breadcrumb` is reserved for later.
- [ ] Serialization uses explicit field ordering and does not rely on reflection.
- [ ] Public model/serializer APIs are marked `@ApiStatus.Experimental`.
- [ ] Tests assert representative stable JSON shape and timeline ordering.

## Comments
