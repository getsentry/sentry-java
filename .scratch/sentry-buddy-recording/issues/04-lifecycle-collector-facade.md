# Add Android lifecycle collector and public facade

Status: ready-for-agent
Type: AFK
Blocked by: 03

## Description

Expose Buddy through a tiny public facade and connect the recorder to Android Activity lifecycle callbacks. Developers should be able to explicitly install Buddy with an `Application`, start a recording, record steps, stop the recording, and get screen timeline items from Activity resumes during the active recording window.

## Acceptance criteria

- [ ] `SentryBuddy.install(application)` registers Buddy's Activity lifecycle collector.
- [ ] `SentryBuddy.startRecording(intent)` delegates to the recorder and starts a recording.
- [ ] `SentryBuddy.recordStep(name, data)` delegates to the active recorder.
- [ ] `SentryBuddy.stopRecording()` returns the finished `BuddyFlowRecording`.
- [ ] Activity resume events append `screen` timeline items only while a recording is active.
- [ ] Installing Buddy is explicit; no manifest auto-init is added.
- [ ] Public facade APIs are marked `@ApiStatus.Experimental`.
- [ ] Tests cover facade delegation and Activity resume screen collection behavior.

## Comments
