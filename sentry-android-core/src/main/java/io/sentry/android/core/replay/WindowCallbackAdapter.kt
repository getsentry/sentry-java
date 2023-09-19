package io.sentry.android.core.replay

import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

open class WindowCallbackDelegate(callback: Window.Callback?) : Window.Callback {

    private val original: Window.Callback

    init {
        original = callback ?: EmptyCallback()
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return original.dispatchKeyEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent?): Boolean {
        return original.dispatchKeyShortcutEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        return original.dispatchTouchEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent?): Boolean {
        return original.dispatchTrackballEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        return original.dispatchGenericMotionEvent(event)
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean {
        return original.dispatchPopulateAccessibilityEvent(event)
    }

    override fun onCreatePanelView(featureId: Int): View? {
        return original.onCreatePanelView(featureId)
    }

    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean {
        return original.onCreatePanelMenu(featureId, menu)
    }

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {
        return original.onPreparePanel(featureId, view, menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        return original.onMenuOpened(featureId, menu)
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        return original.onMenuItemSelected(featureId, item)
    }

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
        return original.onWindowAttributesChanged(attrs)
    }

    override fun onContentChanged() {
        return original.onContentChanged()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        return original.onWindowFocusChanged(hasFocus)
    }

    override fun onAttachedToWindow() {
        return original.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        return original.onDetachedFromWindow()
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        return original.onPanelClosed(featureId, menu)
    }

    override fun onSearchRequested(): Boolean {
        return original.onSearchRequested()
    }

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {
        return original.onSearchRequested(searchEvent)
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
        return original.onWindowStartingActionMode(callback)
    }

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int
    ): ActionMode? {
        return original.onWindowStartingActionMode(callback, type)
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        return original.onActionModeStarted(mode)
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        return original.onActionModeFinished(mode)
    }
}

class EmptyCallback : Window.Callback {
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return false
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent?): Boolean = false

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false

    override fun dispatchTrackballEvent(event: MotionEvent?): Boolean = false

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean = false

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean = false

    override fun onCreatePanelView(featureId: Int): View? = null

    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean = false

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean = false

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean = false

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = false

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
    }

    override fun onContentChanged() {
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
    }

    override fun onAttachedToWindow() {
    }

    override fun onDetachedFromWindow() {
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
    }

    override fun onSearchRequested(): Boolean = false

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean = false

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? = null

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int
    ): ActionMode? = null

    override fun onActionModeStarted(mode: ActionMode?) {
    }

    override fun onActionModeFinished(mode: ActionMode?) {
    }
}
