package io.sentry.compose

import android.app.Application
import android.content.ComponentName
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.NoOpLogger
import io.sentry.compose.SentryModifier.sentryTag
import io.sentry.compose.viewhierarchy.ComposeViewHierarchyExporter
import io.sentry.protocol.ViewHierarchyNode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ComposeIntegrationTests {
    // workaround for robolectric tests with composeRule
    // from https://github.com/robolectric/robolectric/pull/4736#issuecomment-1831034882
    @get:Rule(order = 1)
    val addActivityToRobolectricRule =
        object : TestWatcher() {
            override fun starting(description: Description?) {
                super.starting(description)
                val appContext: Application = ApplicationProvider.getApplicationContext()
                Shadows.shadowOf(appContext.packageManager).addActivityIfNotPresent(
                    ComponentName(
                        appContext.packageName,
                        ComponentActivity::class.java.name,
                    ),
                )
            }
        }

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Compose View Hierarchy is exported with the correct tags`() {
        rule.setContent {
            Column {
                Box(modifier = Modifier.sentryTag("sentryTag"))
                Box(modifier = Modifier.testTag("testTag"))
            }
        }

        rule.activityRule.scenario.onActivity { activity ->
            val exporter = ComposeViewHierarchyExporter(NoOpLogger.getInstance())
            val root = ViewHierarchyNode()
            val rootView = activity.findViewById<View>(android.R.id.content)
            val rootComposeView = locateAndroidComposeView(rootView)
            assertNotNull(rootComposeView)

            exporter.export(root, rootComposeView)

            assertNotNull(locateNodeByTag(root, "sentryTag"))
            assertNotNull(locateNodeByTag(root, "testTag"))
        }
    }

    private fun locateAndroidComposeView(root: View?): Any? {
        if (root == null) {
            return null
        }
        if (root.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
            return root
        }
        if (root is ViewGroup) {
            for (child in root.children) {
                val found = locateAndroidComposeView(child)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun locateNodeByTag(
        root: ViewHierarchyNode,
        tag: String,
    ): ViewHierarchyNode? {
        if (root.tag == tag) {
            return root
        }

        val children = root.children
        if (children != null) {
            for (child in children) {
                val found = locateNodeByTag(child, tag)
                if (found != null) {
                    return found
                }
            }
        }

        return null
    }
}
