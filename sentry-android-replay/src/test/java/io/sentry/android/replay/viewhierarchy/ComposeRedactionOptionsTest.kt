package io.sentry.android.replay.viewhierarchy

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.compose.AsyncImage
import io.sentry.android.replay.sentryReplayIgnore
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ComposeRedactionOptionsTest {

    val composeTestRule = ComposeTestRule()

    @Before
    fun setup() {
        System.setProperty("robolectric.areWindowsMarkedVisible", "true")
    }
}

private class ExampleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Image(
                    painter = painterResource(IR.drawable.logo_pocket_casts),
                    contentDescription = "LOGO",
                    colorFilter = ColorFilter.tint(Color.Black),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                AsyncImage(
                    model = "https://i.imgur.com/tie6A3J.jpeg",
                    contentDescription = null,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                TextField(
                    value = user,
                    onValueChange = { newText ->
                        user = newText
                    }
                )
                Text("Random repo")
                Button(
                    onClick = {},
                    modifier = Modifier
                        .testTag("button_list_repos_async")
                        .padding(top = 32.dp)
                ) {
                    Text("Make Request", modifier = Modifier.sentryReplayIgnore())
                }
            }
        }
    }
}
