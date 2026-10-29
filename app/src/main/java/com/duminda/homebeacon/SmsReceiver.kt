package com.duminda.homebeacon

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HomeBeacon"
        private const val ACTION_SMS_SENT = "com.duminda.homebeacon.ACTION_SMS_SENT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        Log.d(TAG, "SMS_RECEIVED intent received")

        val prefs = Prefs(context)
        if (!prefs.isRepliesEnabled) {
            Log.d(TAG, "Replies are OFF in settings. Ignoring SMS.")
            return
        }

        val trustedNumbers = prefs.trustedNumbers
        if (trustedNumbers.isEmpty()) {
            Log.d(TAG, "No trusted numbers configured in settings. Ignoring SMS.")
            return
        }

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) {
            Log.d(TAG, "No SMS messages extracted from intent.")
            return
        }

        val senderNumber = msgs.firstOrNull()?.originatingAddress ?: return
        Log.d(TAG, "SMS received from sender: $senderNumber (Trusted numbers: $trustedNumbers)")

        val isMatch = trustedNumbers.any { trusted -> isNumberMatch(senderNumber, trusted) }
        if (!isMatch) {
            Log.d(TAG, "Sender $senderNumber does not match any trusted number in $trustedNumbers. Ignoring.")
            return
        }

        val fullBody = msgs.joinToString("") { it.messageBody ?: "" }.trim()
        val triggerPhrase = prefs.triggerPhrase.trim()
        val secretCode = prefs.secretCode.trim()
        val triggerWord = prefs.triggerWord.trim()

        val isTriggerMatch = when {
            triggerPhrase.isNotBlank() && fullBody.equals(triggerPhrase, ignoreCase = true) -> true
            secretCode.isNotBlank() && fullBody.equals(secretCode, ignoreCase = true) -> true
            secretCode.isNotBlank() && triggerWord.isNotBlank() && fullBody.equals("$triggerWord $secretCode", ignoreCase = true) -> true
            else -> false
        }

        Log.d(TAG, "SMS body: '$fullBody', Configured Phrase: '$triggerPhrase', Match: $isTriggerMatch")

        if (!isTriggerMatch) {
            Log.d(TAG, "SMS body does not match trigger. Ignoring.")
            return
        }

        Log.d(TAG, "Trigger matched! Starting location fetch...")
        val pendingResult = goAsync()
        fetchLocationAndReply(context, pendingResult, senderNumber)
    }

    private fun isNumberMatch(sender: String, trusted: String): Boolean {
        val cleanSender = sender.filter { it.isDigit() }
        val cleanTrusted = trusted.filter { it.isDigit() }
        if (cleanSender.isEmpty() || cleanTrusted.isEmpty()) return false

        val matchLength = minOf(cleanSender.length, cleanTrusted.length, 9)
        val senderDigits = cleanSender.takeLast(matchLength)
        val trustedDigits = cleanTrusted.takeLast(matchLength)

        val matches = senderDigits == trustedDigits
        Log.d(TAG, "Number comparison: senderDigits=$senderDigits, trustedDigits=$trustedDigits, match=$matches")
        return matches
    }

    private fun fetchLocationAndReply(
        context: Context,
        pendingResult: PendingResult,
        senderNumber: String
    ) {
        val handled = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val cancellationTokenSource = CancellationTokenSource()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        fun sendReplyAndFinish(location: Location?, isFallback: Boolean) {
            if (handled.compareAndSet(false, true)) {
                cancellationTokenSource.cancel()
                mainHandler.removeCallbacksAndMessages(null)
                try {
                    sendSmsAndNotify(context, senderNumber, location, isFallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in sendSmsAndNotify", e)
                } finally {
                    pendingResult.finish()
                    Log.d(TAG, "pendingResult.finish() called")
                }
            }
        }

        fun fallbackToLastLocation() {
            Log.d(TAG, "Executing fallback to last known location...")
            try {
                fusedLocationClient.lastLocation.addOnCompleteListener { lastTask ->
                    val fusedLoc = if (lastTask.isSuccessful) lastTask.result else null
                    val bestLoc = getBestFallbackLocation(context, fusedLoc)
                    Log.d(TAG, "Fallback location obtained: $bestLoc")
                    sendReplyAndFinish(bestLoc, isFallback = true)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on lastLocation", e)
                val bestLoc = getBestFallbackLocation(context, null)
                sendReplyAndFinish(bestLoc, isFallback = true)
            }
        }

        val timeoutRunnable = Runnable {
            Log.d(TAG, "Location request timed out (8s limit). Triggering fallback.")
            fallbackToLastLocation()
        }

        mainHandler.postDelayed(timeoutRunnable, 8000)

        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnCompleteListener { currentTask ->
                if (currentTask.isSuccessful && currentTask.result != null) {
                    Log.d(TAG, "getCurrentLocation succeeded: ${currentTask.result}")
                    sendReplyAndFinish(currentTask.result, isFallback = false)
                } else {
                    Log.d(TAG, "getCurrentLocation returned null or failed. Falling back.")
                    fallbackToLastLocation()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on getCurrentLocation", e)
            fallbackToLastLocation()
        }
    }

    private fun getBestFallbackLocation(context: Context, fusedLastLoc: Location?): Location? {
        val locations = mutableListOf<Location>()
        if (fusedLastLoc != null) {
            locations.add(fusedLastLoc)
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (provider in providers) {
                try {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        Log.d(TAG, "LocationManager provider '$provider' returned location: $loc")
                        locations.add(loc)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException for provider $provider", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking provider $provider", e)
                }
            }
        }

        val best = locations.maxByOrNull { it.time }
        Log.d(TAG, "Best location selected from ${locations.size} sources: $best")
        return best
    }

    private fun sendSmsAndNotify(
        context: Context,
        senderNumber: String,
        location: Location?,
        isFallback: Boolean
    ) {
        val replyText = if (location != null) {
            val lat = String.format(Locale.US, "%.6f", location.latitude)
            val lon = String.format(Locale.US, "%.6f", location.longitude)
            val acc = location.accuracy.roundToInt()
            val timeStr = if (!isFallback) {
                "just now"
            } else {
                val ageMillis = System.currentTimeMillis() - location.time
                val ageMinutes = (ageMillis / 60000).toInt()
                if (ageMinutes < 1) "1 min ago" else "$ageMinutes min ago"
            }
            "Lat $lat, Lon $lon (+/-$acc m, $timeStr)\nhttps://maps.google.com/?q=$lat,$lon"
        } else {
            "No GPS fix yet. Please try again in a minute."
        }

        Log.d(TAG, "Sending SMS reply to $senderNumber: '$replyText'")

        val smsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                } else {
                    context.getSystemService(SmsManager::class.java)
                }
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system SmsManager", e)
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: @Suppress("DEPRECATION") SmsManager.getDefault()

        val sentIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_SMS_SENT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val resCode = resultCode
                Log.d(TAG, "SMS Sent Broadcast result code: $resCode")
                try {
                    context.applicationContext.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                if (resCode == Activity.RESULT_OK) {
                    Log.d(TAG, "SMS sent successfully to network!")
                    showNotification(context, senderNumber)
                } else {
                    Log.e(TAG, "SMS send failed at telephony layer. Result code: $resCode. " +
                            "(On Xiaomi/HyperOS, check App Info -> Other Permissions -> Send SMS!)")
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                context.applicationContext,
                sentReceiver,
                IntentFilter(ACTION_SMS_SENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering sentReceiver", e)
        }

        try {
            val parts = try {
                smsManager.divideMessage(replyText)
            } catch (e: Exception) {
                Log.e(TAG, "divideMessage failed", e)
                arrayListOf(replyText)
            }

            if (parts.size <= 1) {
                smsManager.sendTextMessage(senderNumber, null, replyText, sentIntent, null)
            } else {
                val sentIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) {
                        add(sentIntent)
                    }
                }
                smsManager.sendMultipartTextMessage(senderNumber, null, parts, sentIntents, null)
            }
            Log.d(TAG, "SmsManager send invocation completed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invoke SmsManager send", e)
        }
    }

    private fun showNotification(context: Context, senderNumber: String) {
        val channelId = "home_beacon_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Location Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when location replies are sent"
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Location sent")
            .setContentText("Replied to $senderNumber with your current location")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            Log.d(TAG, "Notification posted successfully!")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException posting notification", e)
        }
    }
}
