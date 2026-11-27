package io.sentry.uitest.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import coil3.compose.AsyncImage
import java.util.concurrent.CountDownLatch

class ComposeActivity : AppCompatActivity() {
  companion object {
    private const val ITEM_COUNT: Int = 100

    @Volatile var imageLoaded = CountDownLatch(1)
    @Volatile var imageBounds = Rect.Zero
  }

  // two colors, so a replay frame shows whether the image is masked: a mask is one color
  private val image =
    createBitmap(2, 1).apply {
      this[0, 0] = android.graphics.Color.BLACK
      this[1, 0] = android.graphics.Color.WHITE
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    imageLoaded = CountDownLatch(1)

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          LazyColumn(modifier = Modifier.testTag("list")) {
            item {
              Box(
                modifier =
                  Modifier.background(Color.Gray)
                    .fillParentMaxWidth()
                    .fillParentMaxHeight()
                    .clickable {
                      // no-op
                    }
                    .testTag("button_login"),
              ) {
                Text("Login")
                // at the top, away from the center that the UI tests tap
                AsyncImage(
                  model = image,
                  contentDescription = null,
                  contentScale = ContentScale.FillBounds,
                  onSuccess = { imageLoaded.countDown() },
                  modifier =
                    Modifier.align(Alignment.TopCenter)
                      .padding(top = 48.dp)
                      .size(96.dp)
                      .onGloballyPositioned { imageBounds = it.boundsInWindow() },
                )
              }
            }
            items(ITEM_COUNT) {
              Box(modifier = Modifier.size(96.dp).padding(8.dp).background(Color.Gray))
            }
          }
        }
      }
    }
  }
}
