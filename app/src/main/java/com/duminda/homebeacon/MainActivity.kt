package com.duminda.homebeacon

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var etNewTrustedNumber: EditText
    private lateinit var llTrustedNumbersContainer: LinearLayout
    private lateinit var tvTrustedCountBadge: TextView
    private lateinit var etTriggerPhrase: EditText
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

        // Apply saved theme mode
        applyThemeMode(prefs.themeMode)

        // Root DrawerLayout
        drawerLayout = DrawerLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Main Content Layout
        val mainContentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getBackgroundColor())
        }

        // Top App Bar
        val topAppBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dpToPx(16)
            setPadding(p, dpToPx(12), p, dpToPx(12))
            setBackgroundColor(getCardColor())
            elevation = dpToPx(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnMenu = TextView(this).apply {
            text = "☰"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(getTextColor())
            setPadding(0, 0, dpToPx(16), 0)
            setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
        topAppBar.addView(btnMenu)

        val tvAppTitle = TextView(this).apply {
            text = "Home Beacon"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getTextColor())
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        topAppBar.addView(tvAppTitle)

        mainContentLayout.addView(topAppBar)

        // Scrollable Settings Content
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
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

        // Switch: Location replies ON
        swEnableReplies = Switch(this).apply {
            text = "Location replies ON"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(getTextColor())
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

        // Section: Trusted Phone Numbers
        val tvTrustedLabel = TextView(this).apply {
            text = "Trusted Phone Numbers:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getTextColor())
        }
        container.addView(tvTrustedLabel)

        tvTrustedCountBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getSubtextColor())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(2), 0, dpToPx(8))
            layoutParams = lp
        }
        container.addView(tvTrustedCountBadge)

        // Input row to add new trusted number
        val addNumberLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(12))
            layoutParams = lp
        }

        etNewTrustedNumber = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "+94771234567 or 0771234567"
            setTextColor(getTextColor())
            setHintTextColor(getSubtextColor())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val lp = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            layoutParams = lp
        }
        addNumberLayout.addView(etNewTrustedNumber)

        val btnAddNumber = Button(this).apply {
            text = "Add"
            setOnClickListener {
                val input = etNewTrustedNumber.text.toString().trim()
                if (input.isNotBlank()) {
                    if (prefs.addTrustedNumber(input)) {
                        etNewTrustedNumber.text.clear()
                        refreshTrustedNumbersList()
                        Toast.makeText(this@MainActivity, "Added trusted number", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Number already added", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                }
            }
        }
        addNumberLayout.addView(btnAddNumber)
        container.addView(addNumberLayout)

        // Container for list of trusted numbers
        llTrustedNumbersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(16))
            layoutParams = lp
        }
        container.addView(llTrustedNumbersContainer)

        // Section: Trigger Phrase
        val tvTriggerPhraseLabel = TextView(this).apply {
            text = "Trigger Phrase / Command:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getTextColor())
        }
        container.addView(tvTriggerPhraseLabel)

        etTriggerPhrase = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "e.g. Where are you or WHERE 1234"
            setText(prefs.triggerPhrase)
            setTextColor(getTextColor())
            setHintTextColor(getSubtextColor())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(4), 0, dpToPx(16))
            layoutParams = lp
        }
        container.addView(etTriggerPhrase)

        // Section: Secret Code (Optional secondary code)
        val tvCodeLabel = TextView(this).apply {
            text = "Secret Code (Optional Backup Code):"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getTextColor())
        }
        container.addView(tvCodeLabel)

        etSecretCode = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "e.g. 1234"
            setText(prefs.secretCode)
            setTextColor(getTextColor())
            setHintTextColor(getSubtextColor())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(4), 0, dpToPx(16))
            layoutParams = lp
        }
        container.addView(etSecretCode)

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
            lp.setMargins(0, 0, 0, dpToPx(12))
            layoutParams = lp
        }
        container.addView(btnSave)

        // Button: Enable Autostart (Xiaomi / Redmi / Background execution)
        val btnAutostart = Button(this).apply {
            text = "⚡ Enable Autostart (Xiaomi / Background)"
            setOnClickListener {
                openAutostartSettings()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(24))
            layoutParams = lp
        }
        container.addView(btnAutostart)

        // Status Card/TextView
        tvStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(getTextColor())
            val p = dpToPx(12)
            setPadding(p, p, p, p)
            setBackgroundColor(getCardColor())
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
                    "Send from any trusted number your exact Trigger Phrase (e.g. 'Where are you' or 'WHERE 1234').\n" +
                    "Case-insensitive. Matching is 100% customizable.\n\n" +
                    "Background Execution Note:\n" +
                    "On Xiaomi/Redmi/HyperOS devices, tap 'Enable Autostart' above and set Battery Saver to 'No restrictions'. " +
                    "This ensures the SMS receiver runs when the app is closed."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(getSubtextColor())
        }
        container.addView(tvHelp)

        scrollView.addView(container)
        mainContentLayout.addView(scrollView)

        // Add main content layout to DrawerLayout
        drawerLayout.addView(mainContentLayout)

        // Navigation Drawer Sliding View
        val drawerView = createNavDrawerView()
        val drawerLp = DrawerLayout.LayoutParams(
            dpToPx(280),
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = GravityCompat.START
        }
        drawerView.layoutParams = drawerLp
        drawerLayout.addView(drawerView)

        setContentView(drawerLayout)

        refreshTrustedNumbersList()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshTrustedNumbersList()
    }

    private fun createNavDrawerView(): View {
        val navContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getCardColor())
            val p = dpToPx(16)
            setPadding(p, dpToPx(32), p, p)
        }

        // Drawer Header
        val tvHeader = TextView(this).apply {
            text = "Home Beacon"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getTextColor())
        }
        navContainer.addView(tvHeader)

        val tvSubHeader = TextView(this).apply {
            text = "Family Location Beacon"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getSubtextColor())
            setPadding(0, 0, 0, dpToPx(24))
        }
        navContainer.addView(tvSubHeader)

        // Theme Mode Section
        val tvThemeLabel = TextView(this).apply {
            text = "App Theme / Dark Mode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getTextColor())
            setPadding(0, 0, 0, dpToPx(8))
        }
        navContainer.addView(tvThemeLabel)

        val radioGroupTheme = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(24))
            layoutParams = lp
        }

        val rbSystem = RadioButton(this).apply {
            text = "📱 System Default"
            setTextColor(getTextColor())
            id = View.generateViewId()
        }
        val rbLight = RadioButton(this).apply {
            text = "☀️ Light Mode"
            setTextColor(getTextColor())
            id = View.generateViewId()
        }
        val rbDark = RadioButton(this).apply {
            text = "🌙 Dark Mode"
            setTextColor(getTextColor())
            id = View.generateViewId()
        }

        radioGroupTheme.addView(rbSystem)
        radioGroupTheme.addView(rbLight)
        radioGroupTheme.addView(rbDark)

        when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> rbLight.isChecked = true
            Prefs.THEME_DARK -> rbDark.isChecked = true
            else -> rbSystem.isChecked = true
        }

        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                rbLight.id -> Prefs.THEME_LIGHT
                rbDark.id -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            if (newMode != prefs.themeMode) {
                prefs.themeMode = newMode
                applyThemeMode(newMode)
            }
        }
        navContainer.addView(radioGroupTheme)

        // System Settings Links
        val btnAutostartNav = Button(this).apply {
            text = "⚡ Enable Autostart"
            setOnClickListener {
                openAutostartSettings()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(12))
            layoutParams = lp
        }
        navContainer.addView(btnAutostartNav)

        val btnSystemSettings = Button(this).apply {
            text = "⚙ App System Settings"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(12))
            layoutParams = lp
        }
        navContainer.addView(btnSystemSettings)

        val btnBatterySettings = Button(this).apply {
            text = "🔋 Battery Optimization"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(24))
            layoutParams = lp
        }
        navContainer.addView(btnBatterySettings)

        // Footer info
        val tvFooter = TextView(this).apply {
            text = "Offline GPS & SMS Tool\nNo Mobile Data / Wi-Fi Required"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getSubtextColor())
        }
        navContainer.addView(tvFooter)

        return navContainer
    }

    private fun openAutostartSettings() {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutostartManageActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.looper", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        )

        for (intent in intents) {
            try {
                startActivity(intent)
                Toast.makeText(this, "Enable Autostart for Home Beacon", Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun applyThemeMode(mode: Int) {
        val targetMode = when (mode) {
            Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    private fun refreshTrustedNumbersList() {
        llTrustedNumbersContainer.removeAllViews()
        val set = prefs.trustedNumbers
        tvTrustedCountBadge.text = "${set.size} trusted number(s) saved"

        if (set.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "No trusted numbers added yet."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(getSubtextColor())
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            llTrustedNumbersContainer.addView(tvEmpty)
            return
        }

        for (num in set) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = dpToPx(8)
                setPadding(p, p, p, p)
                setBackgroundColor(getCardColor())
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, dpToPx(6))
                layoutParams = lp
            }

            val tvNum = TextView(this).apply {
                text = num
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(getTextColor())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            row.addView(tvNum)

            val btnDelete = TextView(this).apply {
                text = " ✕ "
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(0xFFFF5252.toInt())
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
                setOnClickListener {
                    prefs.removeTrustedNumber(num)
                    refreshTrustedNumbersList()
                    updateStatus()
                    Toast.makeText(this@MainActivity, "Removed $num", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(btnDelete)

            llTrustedNumbersContainer.addView(row)
        }
    }

    private fun saveSettings() {
        val input = etNewTrustedNumber.text.toString().trim()
        if (input.isNotBlank()) {
            prefs.addTrustedNumber(input)
            etNewTrustedNumber.text.clear()
        }
        prefs.triggerPhrase = etTriggerPhrase.text.toString().trim()
        prefs.secretCode = etSecretCode.text.toString().trim()
        refreshTrustedNumbersList()
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun advancePermissionsFlow() {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return
            }
        }

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
        val trustedCount = prefs.trustedNumbers.size
        val triggerPhrase = prefs.triggerPhrase.ifBlank { "(None)" }

        val sb = StringBuilder()
        sb.append("Permission & Configuration Status:\n\n")
        sb.append(if (smsGranted) "✔ SMS access (Receive & Send)" else "✘ SMS access (Receive & Send)").append("\n")
        sb.append(if (locationGranted) "✔ Location access" else "✘ Location access").append("\n")
        sb.append(if (bgLocationGranted) "✔ Location \"Allow all the time\"" else "✘ Location \"Allow all the time\"").append("\n")
        sb.append(if (batteryUnrestricted) "✔ Battery unrestricted" else "✘ Battery unrestricted").append("\n")
        sb.append(if (repliesOn) "✔ Location replies: ON" else "✘ Location replies: OFF").append("\n")
        sb.append("✔ Trigger Phrase: '$triggerPhrase'").append("\n")
        sb.append("✔ Trusted numbers configured: $trustedCount")

        tvStatus.text = sb.toString()
    }

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getBackgroundColor(): Int {
        return if (isDarkMode()) 0xFF121212.toInt() else 0xFFF5F5F7.toInt()
    }

    private fun getCardColor(): Int {
        return if (isDarkMode()) 0xFF1E1E20.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun getTextColor(): Int {
        return if (isDarkMode()) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
    }

    private fun getSubtextColor(): Int {
        return if (isDarkMode()) 0xFFA0A0A5.toInt() else 0xFF6C6C70.toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
