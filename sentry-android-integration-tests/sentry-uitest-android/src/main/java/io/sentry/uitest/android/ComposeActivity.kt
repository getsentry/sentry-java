package io.sentry.uitest.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

class ComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.testTag("list")
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .background(Color.Gray)
                                    .fillParentMaxWidth()
                                    .height(100.dp)
                                    .clickable {
                                        // no-op
                                    }
                                    .testTag("button_login")
                            ) {
                                Text("Login")
                            }
                        }
                        items(100) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .padding(8.dp)
                                    .background(Color.Gray)
                            )
                        }
                    }
                }
            }
        }
    }
}
