package androidx.compose.foundation

import androidx.compose.ui.Modifier

/**
 * Stub classes used by [io.sentry.compose.gestures.ComposeGestureTargetLocatorTest] so that
 * Mockito mocks of these classes return the correct [Class.getName] values at runtime.
 */
internal open class ClickableElement : Modifier.Element

internal open class CombinedClickableElement : Modifier.Element

internal open class ScrollingLayoutElement : Modifier.Element

internal open class ScrollingContainerElement : Modifier.Element
