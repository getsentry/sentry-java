# Implement recorder lifecycle and Sentry correlation

Status: ready-for-agent
Type: AFK
Blocked by: 02

## Description

Implement the core Buddy recorder lifecycle around the v1 recording model. The recorder should support one active flow recording at a time, explicit developer steps, summary derivation, Buddy-owned Sentry tags, and a root Sentry transaction that anchors the recording window.

## Acceptance criteria

- [ ] Starting a recording creates a recording ID, records flow intent, records `recording_started`, and enters active state.
- [ ] Starting a second recording while one is active fails deterministically.
- [ ] Recording a step while active appends a `step` timeline item with elapsed time and optional data.
- [ ] Recording a step while inactive fails deterministically.
- [ ] Stopping a recording appends `recording_stopped`, derives summary data, exits active state, and returns `BuddyFlowRecording`.
- [ ] Starting a recording sets Buddy scope tags under `sentry.buddy.*`.
- [ ] Starting a recording starts a root transaction named `Sentry Buddy Recording: <flow_slug>` with operation `ui.flow_recording`.
- [ ] Stopping a recording finishes the root transaction and removes Buddy's known scope tags.
- [ ] Sentry correlation fields include available trace/span identifiers from the root transaction.
- [ ] Tests cover lifecycle, one-active-recording behavior, deterministic failures, summary derivation, Sentry tag calls, transaction finish, and tag cleanup through fakes.

## Comments
