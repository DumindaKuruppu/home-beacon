package com.duminda.homebeacon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var etTrustedNumber: EditText
    private lateinit var etSecretCode: EditText
    private lateinit var swEnableReplies: Switch
    private lateinit var tvStatus: TextView

    private val group1PermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateStatus()
    }

    private val bgLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpToPx(16)
            setPadding(p, p, p, p)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        val tvTitle = TextView(this).apply {
            text = "Home Beacon Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            val mb = dpToPx(16)
            setPadding(0, 0, 0, mb)
        }
        container.addView(tvTitle)

        // Trusted phone number label & edit text
        val tvNumberLabel = TextView(this).apply {
            text = "Trusted Phone Number:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        container.addView(tvNumberLabel)

        etTrustedNumber = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "e.g. +94771234567 or 0771234567"
            setText(prefs.trustedNumber)
            val mb = dpToPx(12)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(4), 0, mb)
            layoutParams = lp
        }
        container.addView(etTrustedNumber)

        // Secret code label & edit text
        val tvCodeLabel = TextView(this).apply {
            text = "Secret Code:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        container.addView(tvCodeLabel)

        etSecretCode = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "e.g. 1234"
            setText(prefs.secretCode)
            val mb = dpToPx(16)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(4), 0, mb)
            layoutParams = lp
        }
        container.addView(etSecretCode)

        // Switch: Location replies ON
        swEnableReplies = Switch(this).apply {
            text = "Location replies ON"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isChecked = prefs.isRepliesEnabled
            setOnCheckedChangeListener { _, isChecked ->
                prefs.isRepliesEnabled = isChecked
                updateStatus()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(16))
            layoutParams = lp
        }
        container.addView(swEnableReplies)

        // Button: Save & set up permissions
        val btnSave = Button(this).apply {
            text = "Save & set up permissions"
            setOnClickListener {
                saveSettings()
                advancePermissionsFlow()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(24))
            layoutParams = lp
        }
        container.addView(btnSave)

        // Status Card/TextView
        tvStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val p = dpToPx(12)
            setPadding(p, p, p, p)
            setBackgroundColor(0xFFEFEFEF.toInt())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(24))
            layoutParams = lp
        }
        container.addView(tvStatus)

        // Help text
        val tvHelp = TextView(this).apply {
            text = "Trigger SMS Format:\n" +
                    "Send from trusted number: WHERE <secret code>\n" +
                    "(Case-insensitive, e.g. WHERE 1234)\n\n" +
                    "Transparency Note:\n" +
                    "A visible notification is posted on this device every time a location reply is sent. " +
                    "This app functions solely on GPS and normal SMS without mobile data or Wi-Fi."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF666666.toInt())
        }
        container.addView(tvHelp)

        scrollView.addView(container)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun saveSettings() {
        prefs.trustedNumber = etTrustedNumber.text.toString().trim()
        prefs.secretCode = etSecretCode.text.toString().trim()
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun advancePermissionsFlow() {
        // Step 1: Group 1 permissions
        val missingGroup1 = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            missingGroup1.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            missingGroup1.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingGroup1.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingGroup1.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingGroup1.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missingGroup1.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missingGroup1.isNotEmpty()) {
            group1PermissionLauncher.launch(missingGroup1.toTypedArray())
            return
        }

        // Step 2: Background location permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return
            }
        }

        // Step 3: Battery optimization
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        Toast.makeText(this, "All permissions and battery settings are set up!", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun updateStatus() {
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val bgLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName)

        val repliesOn = prefs.isRepliesEnabled

        val sb = StringBuilder()
        sb.append("Permission & Configuration Status:\n\n")
        sb.append(if (smsGranted) "✔ SMS access (Receive & Send)" else "✘ SMS access (Receive & Send)").append("\n")
        sb.append(if (locationGranted) "✔ Location access" else "✘ Location access").append("\n")
        sb.append(if (bgLocationGranted) "✔ Location \"Allow all the time\"" else "✘ Location \"Allow all the time\"").append("\n")
        sb.append(if (batteryUnrestricted) "✔ Battery unrestricted" else "✘ Battery unrestricted").append("\n")
        sb.append(if (repliesOn) "✔ Location replies: ON" else "✘ Location replies: OFF")

        tvStatus.text = sb.toString()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
