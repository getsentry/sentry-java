@file:OptIn(ExperimentalComposeUiApi::class)

package io.sentry.samples.android.compose

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import io.sentry.android.replay.sentryReplayUnmask
import io.sentry.compose.SentryTraced
import io.sentry.compose.withSentryObservableEffect
import io.sentry.samples.android.GithubAPI
import kotlinx.coroutines.launch
import io.sentry.samples.android.R as IR

class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController().withSentryObservableEffect()
            SampleNavigation(navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Landing(
    navigateGithub: () -> Unit,
    navigateGithubWithArgs: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    SentryTraced(tag = "buttons_page") {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            SentryTraced(tag = "button_nav_github") {
                Button(
                    onClick = {
                        navigateGithub()
                    },
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text("Navigate to Github")
                }
            }
            SentryTraced(tag = "button_nav_github_args") {
                Button(
                    onClick = { navigateGithubWithArgs() },
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text("Navigate to Github Page With Args")
                }
            }
            SentryTraced(tag = "button_crash") {
                Button(
                    onClick = { throw RuntimeException("Crash from Compose") },
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text("Crash from Compose")
                }
            }
            SentryTraced(tag = "button_dialog") {
                Button(
                    onClick = {
                        showDialog = true
                    },
                    modifier = Modifier
                        .testTag("button_show_dialog")
                        .padding(top = 32.dp)
                ) {
                    Text("Show Dialog", modifier = Modifier.sentryReplayUnmask())
                }
            }
            if (showDialog) {
                BasicAlertDialog(
                    onDismissRequest = {
                        val orientation = activity.resources.configuration.orientation
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                    content = {
                        Surface(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight(),
                            shape = MaterialTheme.shapes.large,
                            tonalElevation = AlertDialogDefaults.TonalElevation
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                content = {
                                    Text(
                                        "Dialog Title",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(Modifier.size(20.dp))
                                    Text("Dialog Content")
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun Github(
    user: String = "getsentry",
    perPage: Int = 30
) {
    var user by remember { mutableStateOf(TextFieldValue(user)) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(perPage) {
        result = GithubAPI.service.listReposAsync(user.text, perPage).random().full_name
    }

    SentryTraced("github-$user") {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
        ) {
            Image(
                painter = painterResource(IR.drawable.sentry_glyph),
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
            Text("Random repo $result")
            Button(
                onClick = {
                    scope.launch {
                        result =
                            GithubAPI.service.listReposAsync(user.text, perPage).random().full_name
                    }
                },
                modifier = Modifier
                    .testTag("button_list_repos_async")
                    .padding(top = 32.dp)
            ) {
                Text("Make Request", modifier = Modifier.sentryReplayUnmask())
            }
        }
    }
}

@Composable
fun SampleNavigation(navController: NavHostController) {
    SentryTraced(tag = "navhost") {
        NavHost(
            navController = navController,
            startDestination = Destination.Landing.route
        ) {
            composable(Destination.Landing.route) {
                Landing(
                    navigateGithub = { navController.navigate("github") },
                    navigateGithubWithArgs = { navController.navigate("github/spotify?per_page=10") }
                )
            }
            composable(Destination.Github.route) {
                Github()
            }
            composable(
                Destination.GithubWithArgs.route,
                arguments = listOf(
                    navArgument(Destination.USER_ARG) { type = NavType.StringType },
                    navArgument(Destination.PER_PAGE_ARG) {
                        type = NavType.IntType; defaultValue = 10
                    }
                )
            ) {
                Github(
                    it.arguments?.getString(Destination.USER_ARG) ?: "getsentry",
                    it.arguments?.getInt(Destination.PER_PAGE_ARG) ?: 10
                )
            }
        }
    }
}

sealed class Destination(
    val route: String
) {
    object Landing : Destination("landing")
    object Github : Destination("github")
    object GithubWithArgs : Destination("github/{$USER_ARG}?$PER_PAGE_ARG={$PER_PAGE_ARG}")

    companion object {
        const val USER_ARG = "user"
        const val PER_PAGE_ARG = "per_page"
    }
}
