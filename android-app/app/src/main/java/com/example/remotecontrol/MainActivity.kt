package com.example.remotecontrol

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var pairingCode: String

    private val screenCaptureRequest =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val relayUrl = findViewById<EditText>(R.id.relayUrlInput).text.toString().trim()
                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                    putExtra(ScreenCaptureService.EXTRA_RELAY_URL, relayUrl)
                    putExtra(ScreenCaptureService.EXTRA_CODE, pairingCode)
                }
                startForegroundService(intent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("remotecontrol", Context.MODE_PRIVATE)

        // Reuse the same relay URL and pairing code across app restarts.
        val savedUrl = prefs.getString("relayUrl", "")
        findViewById<EditText>(R.id.relayUrlInput).setText(savedUrl)

        pairingCode = prefs.getString("pairingCode", null) ?: RelayClient.generateCode().also {
            prefs.edit().putString("pairingCode", it).apply()
        }
        findViewById<TextView>(R.id.codeText).text = "Pairing code: $pairingCode"

        findViewById<Button>(R.id.btnEnableAccessibility).setOnClickListener {
            // Android does not allow apps to auto-enable an Accessibility Service —
            // this is a deliberate OS security restriction. User must tap it on themselves.
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStartCapture).setOnClickListener {
            val relayUrl = findViewById<EditText>(R.id.relayUrlInput).text.toString().trim()
            if (relayUrl.isEmpty()) {
                findViewById<TextView>(R.id.statusText).text =
                    "Status: enter your relay server URL first"
                return@setOnClickListener
            }
            prefs.edit().putString("relayUrl", relayUrl).apply()

            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureRequest.launch(mpm.createScreenCaptureIntent())
        }
    }
}
