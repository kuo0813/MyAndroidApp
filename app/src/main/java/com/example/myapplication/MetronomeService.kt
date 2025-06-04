package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class MetronomeService : Service() {
    companion object {
        const val ACTION_START = "com.example.myapplication.action.START"
        const val ACTION_STOP = "com.example.myapplication.action.STOP"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_BEAT_COUNT = "extra_beat_count"

        var seconds: Int = 0
        var beatCount: Int = 0
        var running: Boolean = false
    }

    private lateinit var vibrator: Vibrator
    private lateinit var wakeLock: PowerManager.WakeLock
    private val timerHandler = Handler(Looper.getMainLooper())
    private val beatHandler = Handler(Looper.getMainLooper())

    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (running) {
                seconds++
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val beatRunnable: Runnable = object : Runnable {
        override fun run() {
            if (running) {
                vibrateBeat()
                beatCount++
                beatHandler.postDelayed(this, 667)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MetronomeService::WakeLock")
        wakeLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                beatCount = intent.getIntExtra(EXTRA_BEAT_COUNT, 0)
                if (!running) {
                    startForegroundInternal()
                    running = true
                    timerHandler.postDelayed(timerRunnable, 1000)
                    beatHandler.post(beatRunnable)
                }
            }
            ACTION_STOP -> {
                stopMetronome()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundInternal() {
        val channelId = "metronome_channel"
        val channelName = "Metronome"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.running_in_background))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    private fun stopMetronome() {
        running = false
        timerHandler.removeCallbacks(timerRunnable)
        beatHandler.removeCallbacks(beatRunnable)
    }

    private fun vibrateBeat() {
        val duration = if (beatCount % 2 == 0) 200L else 100L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, 255))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

