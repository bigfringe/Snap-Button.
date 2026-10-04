package com.snapbutton.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.*

class CaptureService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var eye: ImageView
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val cameraSound = MediaActionSound()
    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("snap","Snap Button",NotificationManager.IMPORTANCE_LOW))
        startForeground(11, Notification.Builder(this,"snap")
            .setContentTitle("Snap Button is active")
            .setContentText("Tap the floating eye to take a screenshot")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build())
        cameraSound.load(MediaActionSound.SHUTTER_CLICK)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.UK
        }
        showEye()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection == null) {
            val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("captureData", Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra("captureData")
            if (data != null) {
                projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
                projection?.registerCallback(object: MediaProjection.Callback() {
                    override fun onStop() { stopSelf() }
                }, handler)
                createCaptureDisplay()
            }
        }
        return START_NOT_STICKY
    }

    private fun createCaptureDisplay() {
        val m = resources.displayMetrics
        reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 3)
        virtualDisplay = projection?.createVirtualDisplay(
            "SnapButton", m.widthPixels, m.heightPixels, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler
        )
    }

    private fun showEye() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        eye = ImageView(this).apply {
            setImageResource(R.drawable.file_0000000005608210b369af43ca73cd02)
            contentDescription = "Eye snapshot button"
            setOnClickListener {
                tts?.speak("Ouch!", TextToSpeech.QUEUE_FLUSH, null, "snap_ouch")
                handler.postDelayed({ takeSnap() }, 550)
            }
        }
        val size = (64 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.BOTTOM or Gravity.END
        p.x = 0
        p.y = 0
        wm.addView(eye, p)
    }

    private fun takeSnap() {
        val r = reader ?: return
        while (true) { val old = r.acquireLatestImage() ?: break; old.close() }
        handler.postDelayed({
            eye.visibility = View.INVISIBLE
            handler.postDelayed({ captureNewest(0) }, 120)
        }, 120)
    }

    private fun captureNewest(attempt: Int) {
        val image = reader?.acquireLatestImage()
        if (image == null) {
            if (attempt < 10) handler.postDelayed({ captureNewest(attempt + 1) }, 50)
            else {
                eye.visibility = View.VISIBLE
                Toast.makeText(this, "Try again", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val m = resources.displayMetrics
        val plane = image.planes[0]
        val pixel = plane.pixelStride
        val row = plane.rowStride
        val raw = Bitmap.createBitmap(
            m.widthPixels + (row - pixel * m.widthPixels) / pixel,
            m.heightPixels, Bitmap.Config.ARGB_8888
        )
        raw.copyPixelsFromBuffer(plane.buffer)
        image.close()
        val bmp = Bitmap.createBitmap(raw, 0, 0, m.widthPixels, m.heightPixels)
        raw.recycle()
        save(bmp)
        eye.visibility = View.VISIBLE
    }

    private fun save(bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Snap_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(Date()) + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cameraSound.play(MediaActionSound.SHUTTER_CLICK)
            Toast.makeText(this, "Screenshot saved", Toast.LENGTH_SHORT).show()
        }
        bitmap.recycle()
    }

    override fun onDestroy() {
        if (::eye.isInitialized) runCatching { wm.removeView(eye) }
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        cameraSound.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
