package io.sentry.android.buddy.model

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public data class BuddyRecordingResult
public constructor(public val recording: BuddyFlowRecording, public val recordingJson: String)
