package io.sentry.core.util

import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.hints.ApplyScopeData
import io.sentry.core.hints.Cached
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplyScopeUtilsTest {

    @Test
    fun `if event is Cached, it should not apply scopes data`() {
        assertFalse(ApplyScopeUtils.shouldApplyScopeData(mock<Cached>()))
    }

    @Test
    fun `if event is not Cached, it should apply scopes data`() {
        assertTrue(ApplyScopeUtils.shouldApplyScopeData(null))
    }

    @Test
    fun `if event is ApplyScopeData, it should apply scopes data`() {
        assertTrue(ApplyScopeUtils.shouldApplyScopeData(mock<ApplyScopeData>()))
    }

    @Test
    fun `if event is Cached but also ApplyScopeData, it should apply scopes data`() {
        assertTrue(ApplyScopeUtils.shouldApplyScopeData(mock<ApplyScopeData>()))
    }
}
