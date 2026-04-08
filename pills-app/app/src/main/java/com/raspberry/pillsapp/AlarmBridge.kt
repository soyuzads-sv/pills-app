package com.raspberry.pillsapp

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.RingtoneManager
import android.os.*
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.util.Calendar

class AlarmBridge(private val context: Context) {

    @JavascriptInterface
    fun setAlarms(pillsJson: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences("pills_alarms", Context.MODE_PRIVATE)

        // Cancel old alarms
        val oldCount = prefs.getInt("alarm_count", 0)
        for (i in 0 until oldCount) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pi)
        }

        try {
            val pills = JSONArray(pillsJson)
            var alarmId = 0
            for (i in 0 until pills.length()) {
                val pill = pills.getJSONObject(i)
                val name = pill.getString("name")
                val dose = pill.getString("dose")
                val times = pill.getJSONArray("times")
                for (j in 0 until times.length()) {
                    val timeStr = times.getJSONObject(j).getString("time")
                    val parts = timeStr.split(":")
                    val hour = parts[0].toInt()
                    val min = parts[1].toInt()

                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("name", name)
                        putExtra("dose", dose)
                        putExtra("alarm_id", alarmId)
                    }
                    val pi = PendingIntent.getBroadcast(context, alarmId, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, min)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }

                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pi
                    )
                    alarmId++
                }
            }
            prefs.edit().putInt("alarm_count", alarmId).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: "Таблетка"
        val dose = intent.getStringExtra("dose") ?: ""
        val alarmId = intent.getIntExtra("alarm_id", 0)

        createNotificationChannel(context)

        val notifIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(context, 0, notifIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, "pills_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ Время принять таблетку!")
            .setContentText("$name — $dose")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Извините что отрываю от важных дел, но пора принять лекарства.\n$name — $dose"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setLights(Color.BLUE, 1000, 500)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(context).notify(alarmId, notification)

        // Also speak via TTS
        val tts = android.speech.tts.TextToSpeech(context, null)
        tts.language = java.util.Locale("ru", "RU")
        tts.speak(
            "Извините что отрываю от важных дел, но пора принять лекарства. $name. $dose",
            android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "pill_$alarmId"
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pills_channel",
                "Напоминания о таблетках",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о приёме лекарств"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
