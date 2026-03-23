package com.xiaoredsummary.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.xiaoredsummary.R
import com.xiaoredsummary.api.ClaudeApiClient
import com.xiaoredsummary.data.AppDatabase
import com.xiaoredsummary.data.PrefsManager
import com.xiaoredsummary.data.SummaryEntity
import com.xiaoredsummary.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "subtitle_capture"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.xiaoredsummary.STOP"

        var mediaProjectionResultCode: Int = 0
        var mediaProjectionData: Intent? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var overlayButton: View? = null
    private var statusCard: View? = null
    private var isCapturing = false
    private val capturedLines = mutableListOf<String>()
    private var captureJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = PrefsManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        showOverlayButton()
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        overlayButton?.let { windowManager.removeView(it) }
        statusCard?.let { windowManager.removeView(it) }
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showOverlayButton() {
        val inflater = LayoutInflater.from(this)
        overlayButton = inflater.inflate(R.layout.overlay_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 300
        }

        // Make button draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        overlayButton?.findViewById<ImageButton>(R.id.btnOverlay)?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(overlayButton, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onOverlayButtonClick()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayButton, params)
    }

    private fun onOverlayButtonClick() {
        if (isCapturing) {
            stopCaptureAndSummarize()
        } else {
            startCapture()
        }
    }

    private fun startCapture() {
        if (!prefs.hasApiKey) {
            Toast.makeText(this, "Please set your API key in Settings first", Toast.LENGTH_LONG).show()
            return
        }

        val data = mediaProjectionData
        if (data == null) {
            Toast.makeText(this, "Screen capture permission needed. Please restart the overlay.", Toast.LENGTH_LONG).show()
            return
        }

        isCapturing = true
        capturedLines.clear()
        showStatusCard()

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(mediaProjectionResultCode, data.clone() as Intent)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "SubtitleCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )

        captureJob = scope.launch {
            while (isCapturing) {
                captureFrame()
                delay(1500) // Capture every 1.5 seconds
            }
        }
    }

    private fun captureFrame() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to bottom 30% of screen where subtitles typically appear
            val subtitleRegion = Bitmap.createBitmap(
                bitmap,
                0,
                (bitmap.height * 0.65).toInt(),
                image.width,
                (bitmap.height * 0.35).toInt()
            )

            val inputImage = InputImage.fromBitmap(subtitleRegion, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isNotEmpty()) {
                        addSubtitleLine(text)
                    }
                }

            if (bitmap !== subtitleRegion) bitmap.recycle()
            subtitleRegion.recycle()
        } finally {
            image.close()
        }
    }

    private fun addSubtitleLine(text: String) {
        // Deduplicate: skip if too similar to the last few lines
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (capturedLines.isEmpty() || !isSimilar(line, capturedLines.last())) {
                capturedLines.add(line)
                updateStatusCount()
            }
        }
    }

    private fun isSimilar(a: String, b: String): Boolean {
        if (a == b) return true
        // Simple similarity: if one contains 80%+ of the other
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        if (shorter.length.toFloat() / longer.length > 0.8f && longer.contains(shorter)) return true
        // Character overlap check
        val common = a.toSet().intersect(b.toSet())
        val union = a.toSet().union(b.toSet())
        return if (union.isNotEmpty()) common.size.toFloat() / union.size > 0.85f else false
    }

    private fun showStatusCard() {
        val inflater = LayoutInflater.from(this)
        statusCard = inflater.inflate(R.layout.overlay_status, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 200
        }

        statusCard?.findViewById<Button>(R.id.btnStop)?.setOnClickListener {
            stopCaptureAndSummarize()
        }

        windowManager.addView(statusCard, params)
    }

    private fun updateStatusCount() {
        statusCard?.findViewById<TextView>(R.id.tvCount)?.text = "${capturedLines.size} lines captured"
    }

    private fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        statusCard?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        statusCard = null
    }

    private fun stopCaptureAndSummarize() {
        val lines = capturedLines.toList()
        stopCapture()

        if (lines.isEmpty()) {
            Toast.makeText(this, "No subtitles captured", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Summarizing ${lines.size} lines...", Toast.LENGTH_SHORT).show()

        scope.launch {
            val subtitleText = lines.joinToString("\n")
            val client = ClaudeApiClient(prefs.apiKey)
            val result = client.summarize(subtitleText)

            result.onSuccess { response ->
                val title = extractTitle(response)
                val summary = extractSummary(response)

                val entity = SummaryEntity(
                    title = title,
                    subtitles = subtitleText,
                    summary = summary
                )

                val db = AppDatabase.getInstance(this@OverlayService)
                db.summaryDao().insert(entity)

                handler.post {
                    Toast.makeText(this@OverlayService, "Summary saved!", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                handler.post {
                    Toast.makeText(
                        this@OverlayService,
                        "Error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun extractTitle(response: String): String {
        val titleMatch = Regex("TITLE:\\s*(.+)").find(response)
        return titleMatch?.groupValues?.get(1)?.trim() ?: "Untitled"
    }

    private fun extractSummary(response: String): String {
        val summaryMatch = Regex("SUMMARY:\\s*\\n?([\\s\\S]+)", RegexOption.MULTILINE).find(response)
        return summaryMatch?.groupValues?.get(1)?.trim() ?: response
    }
}
