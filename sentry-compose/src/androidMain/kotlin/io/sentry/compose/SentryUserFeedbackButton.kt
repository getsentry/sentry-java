package io.sentry.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.sentry.Sentry
import io.sentry.SentryFeedbackOptions

@Composable
public fun SentryUserFeedbackButton(
  modifier: Modifier = Modifier,
  configurator: SentryFeedbackOptions.OptionsConfigurator? = null,
  text: String = "Report a Bug",
) {
  Button(modifier = modifier, onClick = { Sentry.showUserFeedbackDialog(configurator) }) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.sentry_user_feedback_compose_button_logo_24),
        contentDescription = null,
      )
      Spacer(Modifier.padding(horizontal = 4.dp))
      Text(text = text)
    }
  }
}
