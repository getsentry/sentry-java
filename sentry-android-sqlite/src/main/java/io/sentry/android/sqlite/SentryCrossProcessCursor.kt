package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.CursorWindow
import android.database.CursorWrapper

/*
 * SQLiteCursor executes the query lazily, when one of getCount() and onMove() is called.
 * Also, by docs, fillWindow() can be used to fill the cursor with data.
 * So we wrap these methods to create a span.
 * Ordinary Cursor methods are delegated through CursorWrapper to avoid adding Sentry frames to
 * app database exceptions that the wrapper did not instrument.
 */
internal class SentryCrossProcessCursor(
  private val delegate: CrossProcessCursor,
  private val spans: OpenHelperSpans,
  private val sql: String,
) : CursorWrapper(delegate), CrossProcessCursor {
  // We have to start the span only the first time, regardless of how many times its methods get
  // called.
  private var isSpanStarted = false

  override fun getCount(): Int {
    if (isSpanStarted) {
      return delegate.count
    }
    isSpanStarted = true
    return spans.performSql(sql) { delegate.count }
  }

  override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
    if (isSpanStarted) {
      return delegate.onMove(oldPosition, newPosition)
    }
    isSpanStarted = true
    return spans.performSql(sql) { delegate.onMove(oldPosition, newPosition) }
  }

  override fun getWindow(): CursorWindow? = delegate.window

  override fun fillWindow(position: Int, window: CursorWindow?) {
    if (isSpanStarted) {
      return delegate.fillWindow(position, window)
    }
    isSpanStarted = true
    return spans.performSql(sql) { delegate.fillWindow(position, window) }
  }
}
