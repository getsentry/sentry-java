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

public fun Modifier.sentryReplayRedact(): Modifier {
    return semantics(
        properties = {
            this[SentryPrivacy] = "redact"
        }
    )
}

public fun Modifier.sentryReplayIgnore(): Modifier {
    return semantics(
        properties = {
            this[SentryPrivacy] = "ignore"
        }
    )
}
