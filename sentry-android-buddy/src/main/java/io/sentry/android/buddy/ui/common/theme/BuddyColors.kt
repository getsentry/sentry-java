package io.sentry.android.buddy.ui.common.theme

import androidx.compose.ui.graphics.Color
import io.sentry.android.buddy.model.Severity

internal val BuddyPurple = Color(0xFF7553FF)

internal val BuddyAccentBubbleChonk = Color(0xFF5827D6)

internal val BuddyAccentBubbleShadow = Color(0xFF44208F)

internal val BuddyAccentBubbleStart = Color(0xFF896CFF)

internal val BuddyAccentBubbleEnd = Color(0xFF6948F5)

internal val BuddyRed = Color(0xFFFF003D)

internal val BuddyRecordingBubbleChonk = Color(0xFFC10000)

internal val BuddyRecordingBubbleShadow = Color(0xFF7E001A)

internal val BuddyRecordingBubbleStart = Color(0xFFFF4D73)

internal val BuddyRecordingBubbleEnd = Color(0xFFFF002B)

internal val BuddyWarningBubbleChonk = Color(0xFF8A4200)

internal val BuddyWarningBubbleShadow = Color(0xFF5A2800)

internal val BuddyWarningBubbleStart = Color(0xFFFFB347)

internal val BuddyWarningBubbleEnd = Color(0xFFFF7A00)

internal val BuddyErrorBubbleChonk = Color(0xFFA4002B)

internal val BuddyErrorBubbleShadow = Color(0xFF6B001C)

internal val BuddyErrorBubbleStart = Color(0xFFFF5B7A)

internal val BuddyErrorBubbleEnd = Color(0xFFFF003D)

internal val BuddyGold = Color(0xFFC47A00)

internal val BuddyGreen = Color(0xFF0F9D58)

internal val BuddyBugGreen = Color(0xFF65D77A)

internal val BuddyQuickDecisionCard = Color(0xFFEAF8EE)

internal val BuddyQuickDecisionPeek = Color(0xFFF5FCF7)

internal val BuddyInk = Color(0xFF171426)

internal val BuddyMuted = Color(0xFF6F6B7A)

internal val BuddyBorder = Color(0xFFE0DDE6)

internal val BuddyCode = Color(0xFFF3F1F6)

internal val BuddySweatshirtPink = Color(0xFFEF6CB7)

internal fun severityColor(severity: Severity): Color =
  when (severity) {
    Severity.HIGH -> BuddyRed
    Severity.MEDIUM -> BuddyGold
    Severity.LOW -> BuddyPurple
  }
