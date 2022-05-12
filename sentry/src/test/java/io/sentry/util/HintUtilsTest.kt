package io.sentry.util

import com.nhaarman.mockitokotlin2.mock
import io.sentry.CustomCachedApplyScopeDataHint
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Cached
import io.sentry.hints.Hints
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HintUtilsTest {

    @Test
    fun `if event is Cached, it should not apply scopes data`() {
        val hints = HintUtils.createWithTypeCheckHint(mock<Cached>())
        assertFalse(HintUtils.shouldApplyScopeData(hints))
    }

    @Test
    fun `if event is not Cached, it should apply scopes data`() {
        assertTrue(HintUtils.shouldApplyScopeData(Hints()))
    }

    @Test
    fun `if event is ApplyScopeData, it should apply scopes data`() {
        val hints = HintUtils.createWithTypeCheckHint(mock<ApplyScopeData>())
        assertTrue(HintUtils.shouldApplyScopeData(hints))
    }

    @Test
    fun `if event is Cached but also ApplyScopeData, it should apply scopes data`() {
        val hints = HintUtils.createWithTypeCheckHint(CustomCachedApplyScopeDataHint())
        assertTrue(HintUtils.shouldApplyScopeData(hints))
    }
}
