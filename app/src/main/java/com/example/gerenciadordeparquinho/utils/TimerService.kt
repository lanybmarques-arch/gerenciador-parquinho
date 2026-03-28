package com.example.gerenciadordeparquinho.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gerenciadordeparquinho.MainActivity
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import kotlinx.coroutines.*

class TimerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForegroundService()
            monitorTimers()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "TIMER_SERVICE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Monitor de Tempo", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gerenciador de Parquinho")
            .setContentText("Monitorando cronômetros ativos...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    private fun monitorTimers() {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(this@TimerService)
            while (isRunning) {
                delay(2000)
                val activeSessions = db.sessionDao().getActiveSessionsList()
                activeSessions.forEach { session ->
                    if (session.remainingSeconds <= 0 && !session.notified && !session.isFinished && !session.isPaused) {
                        showAlertNotification(session.personName, session.toyName)
                        db.sessionDao().insertSession(session.copy(notified = true))
                    }
                }
            }
        }
    }

    private fun showAlertNotification(name: String, toy: String) {
        val channelId = "TIMER_ALERT_CHANNEL"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alertas de Tempo", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TEMPO ESGOTADO!")
            .setContentText("Criança: $name | Brinquedo: $toy")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}
