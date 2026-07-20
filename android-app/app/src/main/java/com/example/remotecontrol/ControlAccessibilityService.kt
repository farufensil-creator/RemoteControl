package com.example.remotecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject

/**
 * Receives JSON commands from the viewer (relayed via RelayClient) and turns them
 * into real touch gestures using the Accessibility API.
 *
 * Expected JSON shapes (sent by viewer.html):
 *   {"type":"tap",   "x":123, "y":456}
 *   {"type":"swipe", "x1":100,"y1":200,"x2":300,"y2":400,"durationMs":200}
 */
class ControlAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ControlA11yService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        RelayClient.onCommand = { json -> handleCommand(json) }
    }

    private fun handleCommand(json: JSONObject) {
        when (json.optString("type")) {
            "tap" -> {
                val x = json.optDouble("x").toFloat()
                val y = json.optDouble("y").toFloat()
                dispatchTap(x, y)
            }
            "swipe" -> {
                val x1 = json.optDouble("x1").toFloat()
                val y1 = json.optDouble("y1").toFloat()
                val x2 = json.optDouble("x2").toFloat()
                val y2 = json.optDouble("y2").toFloat()
                val duration = json.optLong("durationMs", 200L)
                dispatchSwipe(x1, y1, x2, y2, duration)
            }
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for remote control, but required override.
    }

    override fun onInterrupt() {}
}
