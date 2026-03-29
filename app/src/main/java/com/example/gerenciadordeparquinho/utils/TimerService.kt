package com.example.gerenciadordeparquinho.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gerenciadordeparquinho.MainActivity
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import kotlinx.coroutines.*

class TimerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForegroundService()
            monitorAndDecrementTimers()
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
            .setContentText("Monitorando tempos em tempo real...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun monitorAndDecrementTimers() {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(this@TimerService)
            while (isRunning) {
                delay(1000)
                val now = System.currentTimeMillis()
                val activeSessions = db.sessionDao().getActiveSessionsList()
                
                activeSessions.forEach { session ->
                    if (!session.isFinished && !session.isPaused) {
                        if (session.remainingSeconds > 0) {
                            val diffSeconds = (now - session.lastUpdateTimestamp) / 1000
                            if (diffSeconds >= 1) {
                                val newRemaining = (session.remainingSeconds - diffSeconds).coerceAtLeast(0L)
                                val updatedSession = session.copy(
                                    remainingSeconds = newRemaining,
                                    elapsedSecondsInCurrentCycle = session.elapsedSecondsInCurrentCycle + diffSeconds,
                                    lastUpdateTimestamp = now
                                )
                                
                                if (newRemaining <= 0 && !session.notified) {
                                    // 1. DISPARA NOTIFICAÇÃO NO TOPO
                                    showAlertNotification(session.personName, session.toyName)
                                    
                                    // 2. VERIFICA IMPRESSÃO AUTOMÁTICA DE SAÍDA
                                    val autoPrintExit = sharedPrefs.getBoolean("auto_print_exit", false)
                                    val printerMac = sharedPrefs.getString("last_printer_mac", "") ?: ""
                                    val printerSize = sharedPrefs.getString("last_printer_size", "58mm") ?: "58mm"
                                    val printerLogo = sharedPrefs.getString("printer_logo_base64", null)
                                    val customMsg = sharedPrefs.getString("printer_message", "")

                                    if (autoPrintExit && printerMac.isNotEmpty()) {
                                        // FORÇAMOS isFinished = true apenas para o título da nota sair como SAÍDA
                                        BluetoothPrinterHelper.printEntranceTicket(
                                            macAddress = printerMac,
                                            session = updatedSession.copy(isFinished = true),
                                            size = printerSize,
                                            logoBase64 = printerLogo,
                                            customMessage = customMsg
                                        )
                                    }

                                    db.sessionDao().insertSession(updatedSession.copy(notified = true))
                                } else {
                                    db.sessionDao().insertSession(updatedSession)
                                }
                            }
                        }
                    } else {
                        db.sessionDao().insertSession(session.copy(lastUpdateTimestamp = now))
                    }
                }
            }
        }
    }

    private fun showAlertNotification(name: String, toy: String) {
        val channelId = "TIMER_CHANNEL"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alertas de Tempo", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 200, 800)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("TEMPO ESGOTADO!")
            .setContentText("O TEMPO DE ${name.uppercase()} NO ${toy.uppercase()} ACABOU!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(0, 800, 200, 800))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}
