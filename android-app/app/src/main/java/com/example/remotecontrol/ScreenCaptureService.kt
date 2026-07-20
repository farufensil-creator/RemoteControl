package com.example.remotecontrol

import android.app.*
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

/**
 * Captures the screen continuously and pushes JPEG frames to RelayClient,
 * which forwards them over the internet to whichever viewer is watching.
 *
 * Must be started with the Intent (resultCode, data) obtained from
 * MediaProjectionManager.createScreenCaptureIntent() — the user has to
 * approve a system dialog, Android requires this and it cannot be skipped.
 */
class ScreenCaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = DisplayMetrics.DENSITY_DEFAULT

    companion object {
        const val CHANNEL_ID = "screen_capture_channel"
        const val NOTIF_ID = 1
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_RELAY_URL = "relayUrl"
        const val EXTRA_CODE = "pairingCode"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        val relayUrl = intent?.getStringExtra(EXTRA_RELAY_URL)
        val pairingCode = intent?.getStringExtra(EXTRA_CODE)

        if (relayUrl != null && pairingCode != null) {
            RelayClient.connect(relayUrl, pairingCode) { status ->
                android.util.Log.i("ScreenCaptureService", "Relay status: $status")
            }
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val metrics = resources.displayMetrics
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi

            val projectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            startCapture()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "RemoteControlCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image)
                val jpeg = bitmapToJpeg(bitmap)
                RelayClient.sendFrame(jpeg)
                bitmap.recycle()
            } finally {
                image.close()
            }
        }, null)
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
    }

    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        // Quality 40 keeps bandwidth reasonable over WiFi; raise it if you want sharper video.
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
        return stream.toByteArray()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen sharing", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RemoteControl")
            .setContentText("Screen is being shared")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        if (::mediaProjection.isInitialized) mediaProjection.stop()
        RelayClient.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
