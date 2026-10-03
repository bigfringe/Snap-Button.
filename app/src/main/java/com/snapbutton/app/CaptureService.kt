package com.snapbutton.app

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import java.text.SimpleDateFormat
import java.util.*

class CaptureService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var bubble: ImageButton
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val cameraSound = MediaActionSound()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("snap","Snap Button",NotificationManager.IMPORTANCE_LOW))
        startForeground(11, Notification.Builder(this,"snap")
            .setContentTitle("Snap Button is active")
            .setContentText("Floating screenshot button is running")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build())
        showBubble()
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
        virtualDisplay = projection?.createVirtualDisplay("SnapButton", m.widthPixels, m.heightPixels, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
    }

    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.rgb(255, 215, 0))
            background = GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(Color.rgb(255, 235, 59)); setStroke((2 * resources.displayMetrics.density).toInt(), Color.rgb(255, 215, 0)) }
            setPadding(22,22,22,22)
            contentDescription = "Take screenshot"
            setOnClickListener { takeSnap() }
        }
        val size = (64 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.BOTTOM or Gravity.END
        p.x = 0
        p.y = 0
        wm.addView(bubble,p)
    }

    private fun takeSnap() {
        val r = reader ?: return
        while (true) { val old = r.acquireLatestImage() ?: break; old.close() }
        bubble.visibility = View.INVISIBLE
        handler.postDelayed({ captureNewest(0) }, 180)
    }

    private fun captureNewest(attempt:Int) {
        val image = reader?.acquireLatestImage()
        if (image == null) {
            if (attempt < 8) handler.postDelayed({captureNewest(attempt+1)},50)
            else { bubble.visibility=View.VISIBLE; Toast.makeText(this,"Try again",Toast.LENGTH_SHORT).show() }
            return
        }
        val m=resources.displayMetrics
        val plane=image.planes[0]; val pixel=plane.pixelStride; val row=plane.rowStride
        val raw=Bitmap.createBitmap(m.widthPixels+(row-pixel*m.widthPixels)/pixel,m.heightPixels,Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(plane.buffer); image.close()
        val bmp=Bitmap.createBitmap(raw,0,0,m.widthPixels,m.heightPixels); raw.recycle()
        save(bmp); bubble.visibility=View.VISIBLE
    }

    private fun playCameraSound() {
        cameraSound.play(MediaActionSound.SHUTTER_CLICK)
        handler.postDelayed({
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            handler.postDelayed({ tone.release() }, 250)
        }, 120)
    }

    private fun save(b:Bitmap) {
        val v=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"Snap_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.UK).format(Date())+".png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Screenshots")
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v)?.let { u ->
            contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
            playCameraSound()
            Toast.makeText(this,"Screenshot saved",Toast.LENGTH_SHORT).show()
        }
        b.recycle()
    }

    override fun onDestroy() {
        if (::bubble.isInitialized) runCatching { wm.removeView(bubble) }
        virtualDisplay?.release(); reader?.close(); projection?.stop(); cameraSound.release()
        super.onDestroy()
    }
    override fun onBind(intent:Intent?)=null
}