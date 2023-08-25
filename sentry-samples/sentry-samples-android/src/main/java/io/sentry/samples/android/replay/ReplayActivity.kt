package io.sentry.samples.android.replay

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.ReplayRecording
import io.sentry.Sentry
import io.sentry.SentryReplayEvent
import io.sentry.samples.android.R
import kotlin.random.Random

class ReplayActivity : AppCompatActivity() {

    private val viewRecorder: WindowRecorder = WindowRecorder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_replay)

        val generatedNumberTextView = findViewById<TextView>(R.id.generated_number)
        val generatedNumberLabelTextView = findViewById<TextView>(R.id.generated_number_label)
        findViewById<View>(R.id.generate_number).setOnClickListener {
            val number = Random.nextInt(1, 11)
            generatedNumberTextView.text = "$number"
            generatedNumberLabelTextView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        viewRecorder.startRecording(this)
    }

    override fun onPause() {
        super.onPause()
        viewRecorder.stopRecording()
        Sentry.getCurrentHub().configureScope { scope ->
            scope.breadcrumbs.forEach { breadcrumb ->
                if (breadcrumb.timestamp.time > viewRecorder.recorder.startTimeMs &&
                    breadcrumb.timestamp.time < viewRecorder.recorder.endTimeMs &&
                    breadcrumb.category == "ui.click"
                ) {
                    viewRecorder.recorder.addBreadcrumb(breadcrumb)
                }
            }
        }
        val replay = SentryReplayEvent()

        replay.timestamp =
            DateUtils.millisToSeconds(viewRecorder.recorder.endTimeMs.toDouble())

        replay.replayStartTimestamp =
            DateUtils.millisToSeconds(viewRecorder.recorder.startTimeMs.toDouble())
        replay.segmentId = 0

        val replayRecording = ReplayRecording().apply {
            segmentId = 0
            payload = viewRecorder.recorder.recording as List<Any>
        }
        val hint = Hint()
        hint.addReplayRecording(replayRecording)

        Sentry.captureReplay(replay, hint)
    }
}
