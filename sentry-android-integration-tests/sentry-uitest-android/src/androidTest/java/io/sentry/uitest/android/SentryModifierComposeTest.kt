package io.sentry.uitest.android

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.compose.SentryModifier
import io.sentry.compose.SentryModifier.sentryTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SentryModifierComposeTest : BaseUiTest() {

    companion object {
        private const val TAG_VALUE = "ExampleTagValue"
    }

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun sentryModifierAppliesTag() {
        rule.setContent {
            Box(modifier = Modifier.sentryTag(TAG_VALUE))
        }
        rule.onNode(
            SemanticsMatcher(TAG_VALUE) {
                it.config.find { (key, _) -> key.name == SentryModifier.TAG }?.value == TAG_VALUE
            }
        ).assertExists()
    }
}
