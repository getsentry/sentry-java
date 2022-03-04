package io.sentry.util

import com.nhaarman.mockitokotlin2.mock
import io.sentry.CustomCachedApplyScopeDataHint
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Cached
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HintUtilsTest {

    @Test
    fun `if event is Cached, it should not apply scopes data`() {
        val hintsMap = mutableMapOf<String, Any>(SENTRY_TYPE_CHECK_HINT to mock<Cached>())
        assertFalse(HintUtils.shouldApplyScopeData(hintsMap))
    }

    @Test
    fun `if event is not Cached, it should apply scopes data`() {
        assertTrue(HintUtils.shouldApplyScopeData(null))
    }

    @Test
    fun `if event is ApplyScopeData, it should apply scopes data`() {
        val hintsMap = mutableMapOf<String, Any>(SENTRY_TYPE_CHECK_HINT to mock<ApplyScopeData>())
        assertTrue(HintUtils.shouldApplyScopeData(hintsMap))
    }

    @Test
    fun `if event is Cached but also ApplyScopeData, it should apply scopes data`() {
        val hintsMap = mutableMapOf<String, Any>(SENTRY_TYPE_CHECK_HINT to CustomCachedApplyScopeDataHint())
        assertTrue(HintUtils.shouldApplyScopeData(hintsMap))
    }
}
