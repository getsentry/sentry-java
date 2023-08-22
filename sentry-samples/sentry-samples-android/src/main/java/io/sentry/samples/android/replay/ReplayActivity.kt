package io.sentry.samples.android.replay

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.ReplayRecording
import io.sentry.Sentry
import io.sentry.SentryReplayEvent
import io.sentry.samples.android.R

class ReplayActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ReplayActivity"
    }

    private val viewRecorder: WindowRecorder = WindowRecorder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_replay)

        findViewById<View>(R.id.action_replay).setOnClickListener {
            viewRecorder.stopRecording()
            val replay = SentryReplayEvent()

            replay.timestamp =
                DateUtils.millisToSeconds(viewRecorder.recorder.startTimeMs.toDouble())
            replay.replayStartTimestamp =
                DateUtils.millisToSeconds(viewRecorder.recorder.endTimeMs.toDouble())
            replay.segmentId = 0

            val replayRecording = ReplayRecording()
            replayRecording.segmentId = 0
            replayRecording.payload = viewRecorder.recorder.recording

            val hint = Hint()
            hint.addReplayRecording(replayRecording)

            Sentry.captureReplay(replay, hint)
        }
    }

    override fun onResume() {
        super.onResume()
        viewRecorder.startRecording(this)
    }

    override fun onPause() {
        super.onPause()
        viewRecorder.stopRecording()
    }
}
