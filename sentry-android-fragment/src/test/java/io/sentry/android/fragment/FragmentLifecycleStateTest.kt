package io.sentry.android.fragment

import kotlin.test.Test
import kotlin.test.assertEquals

class FragmentLifecycleStateTest {
    @Test
    fun `states contains all states`() {
        assertEquals(FragmentLifecycleState.states, FragmentLifecycleState.values().toSet())
    }
}
