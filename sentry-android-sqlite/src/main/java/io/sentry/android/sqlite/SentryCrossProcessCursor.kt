package io.sentry.android.sqlite

import android.database.CrossProcessCursor
import android.database.CursorWindow

/*
 * SQLiteCursor executes the query lazily, when one of getCount() and onMove() is called.
 * Also, by docs, fillWindow() can be used to fill the cursor with data.
 * So we wrap these methods to create a span.
 * SQLiteCursor is never used directly in the code, but only the Cursor interface.
 *  This means we can use CrossProcessCursor - that extends Cursor - as wrapper, since
 *   CrossProcessCursor is an interface and we can use Kotlin delegation.
 */
internal class SentryCrossProcessCursor(
    private val delegate: CrossProcessCursor,
    private val spanManager: SQLiteSpanManager,
    private val sql: String,
) : CrossProcessCursor by delegate {
    // We have to start the span only the first time, regardless of how many times its methods get called.
    private var isSpanStarted = false

    override fun getCount(): Int {
        if (isSpanStarted) {
            return delegate.count
        }
        isSpanStarted = true
        return spanManager.performSql(sql) {
            delegate.count
        }
    }

    override fun onMove(
        oldPosition: Int,
        newPosition: Int,
    ): Boolean {
        if (isSpanStarted) {
            return delegate.onMove(oldPosition, newPosition)
        }
        isSpanStarted = true
        return spanManager.performSql(sql) {
            delegate.onMove(oldPosition, newPosition)
        }
    }

    override fun fillWindow(
        position: Int,
        window: CursorWindow?,
    ) {
        if (isSpanStarted) {
            return delegate.fillWindow(position, window)
        }
        isSpanStarted = true
        return spanManager.performSql(sql) {
            delegate.fillWindow(position, window)
        }
    }
}
