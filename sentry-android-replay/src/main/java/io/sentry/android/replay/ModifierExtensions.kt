package io.sentry.android.replay

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import io.sentry.android.replay.SentryReplayModifiers.SentryPrivacy

public object SentryReplayModifiers {
    public val SentryPrivacy: SemanticsPropertyKey<String> =
        SemanticsPropertyKey<String>(
            name = "SentryPrivacy",
            mergePolicy = { parentValue, _ -> parentValue },
        )
}

public fun Modifier.sentryReplayMask(): Modifier =
    semantics(
        properties = {
            this[SentryPrivacy] = "mask"
        },
    )

public fun Modifier.sentryReplayUnmask(): Modifier =
    semantics(
        properties = {
            this[SentryPrivacy] = "unmask"
        },
    )
