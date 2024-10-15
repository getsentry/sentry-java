package io.sentry.android.replay

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import io.sentry.android.replay.SentryReplayModifiers.SentryPrivacy

public object SentryReplayModifiers {
    val SentryPrivacy = SemanticsPropertyKey<String>(
        name = "SentryPrivacy",
        mergePolicy = { parentValue, _ -> parentValue }
    )
}

public fun Modifier.sentryReplayMask(): Modifier {
    return semantics(
        properties = {
            this[SentryPrivacy] = "mask"
        }
    )
}

public fun Modifier.sentryReplayUnmask(): Modifier {
    return semantics(
        properties = {
            this[SentryPrivacy] = "unmask"
        }
    )
}
