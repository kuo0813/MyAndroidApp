package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private lateinit var startStopButton: Button
    private lateinit var pauseButton: Button

    private var running = false
    private var paused = false
    private var seconds = 0
    private var beatCount = 0

    private lateinit var vibrator: Vibrator

    private val timerHandler = Handler(Looper.getMainLooper())
    private val beatHandler = Handler(Looper.getMainLooper())

    private fun startServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (running && !paused) {
                seconds++
                val mins = seconds / 60
                val secs = seconds % 60
                timerText.text = String.format("%02d:%02d", mins, secs)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val beatRunnable: Runnable = object : Runnable {
        override fun run() {
            if (running && !paused) {
                vibrateBeat()
                beatCount++
                // 667 ms per beat ≈ 90 BPM
                beatHandler.postDelayed(this, 667)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(R.layout.activity_main)

        timerText = findViewById(R.id.timerText)
        startStopButton = findViewById(R.id.startStopButton)
        pauseButton = findViewById(R.id.pauseButton)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        startStopButton.setOnClickListener {
            if (!running) {
                startMetronome()
            } else {
                stopMetronome()
            }
        }

        pauseButton.setOnClickListener {
            if (running) {
                if (paused) {
                    resumeMetronome()
                } else {
                    pauseMetronome()
                }
            }
        }
    }

    private fun startMetronome() {
        if (MetronomeService.running) {
            val intent = Intent(this, MetronomeService::class.java).apply {
                action = MetronomeService.ACTION_STOP
            }
            startService(intent)
        }
        running = true
        paused = false
        seconds = 0
        beatCount = 0
        timerText.text = "00:00"
        startStopButton.text = getString(R.string.stop)
        pauseButton.text = getString(R.string.pause)
        timerHandler.postDelayed(timerRunnable, 1000)
        beatHandler.post(beatRunnable)
    }

    private fun stopMetronome() {
        if (MetronomeService.running) {
            val intent = Intent(this, MetronomeService::class.java).apply {
                action = MetronomeService.ACTION_STOP
            }
            startService(intent)
        }
        running = false
        paused = false
        timerHandler.removeCallbacks(timerRunnable)
        beatHandler.removeCallbacks(beatRunnable)
        seconds = 0
        beatCount = 0
        timerText.text = "00:00"
        startStopButton.text = getString(R.string.start)
        pauseButton.text = getString(R.string.pause)
    }

    private fun pauseMetronome() {
        paused = true
        timerHandler.removeCallbacks(timerRunnable)
        beatHandler.removeCallbacks(beatRunnable)
        pauseButton.text = getString(R.string.resume)
    }

    private fun resumeMetronome() {
        paused = false
        timerHandler.postDelayed(timerRunnable, 1000)
        beatHandler.post(beatRunnable)
        pauseButton.text = getString(R.string.pause)
    }

    override fun onStop() {
        super.onStop()
        if (running && !paused && !MetronomeService.running) {
            val intent = Intent(this, MetronomeService::class.java).apply {
                action = MetronomeService.ACTION_START
                putExtra(MetronomeService.EXTRA_SECONDS, seconds)
                putExtra(MetronomeService.EXTRA_BEAT_COUNT, beatCount)
            }
            startServiceCompat(intent)
            timerHandler.removeCallbacks(timerRunnable)
            beatHandler.removeCallbacks(beatRunnable)
        }
    }

    override fun onStart() {
        super.onStart()
        if (MetronomeService.running) {
            seconds = MetronomeService.seconds
            beatCount = MetronomeService.beatCount
            val mins = seconds / 60
            val secs = seconds % 60
            timerText.text = String.format("%02d:%02d", mins, secs)
            val intent = Intent(this, MetronomeService::class.java).apply {
                action = MetronomeService.ACTION_STOP
            }
            startService(intent)
            timerHandler.postDelayed(timerRunnable, 1000)
            beatHandler.post(beatRunnable)
        }
    }

    private fun vibrateBeat() {
        val duration = if (beatCount % 2 == 0) 200L else 100L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, 255)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
        beatHandler.removeCallbacksAndMessages(null)
    }
}
