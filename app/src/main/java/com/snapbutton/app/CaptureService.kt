package com.snapbutton.app

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.*

class CaptureService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var eye: ImageView
    private lateinit var buttonContainer: FrameLayout
    private var designSymbol: TextView? = null
    private var chooser: View? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val cameraSound = MediaActionSound()
    private var tts: TextToSpeech? = null
    private var longPressTriggered = false
    private val prefs by lazy { getSharedPreferences("snap_button", MODE_PRIVATE) }
    private val selectedDesign get() = prefs.getInt("design", 2).coerceIn(0, 7)
    private val choiceBitmaps = mutableMapOf<Int, Bitmap>()

    private data class ButtonDesign(val title: String, val preview: String)
    private val designs = listOf(
        ButtonDesign("Water Pistol", "Pew pew, splash!"),
        ButtonDesign("Rocket", "Three, two, one, blast off!"),
        ButtonDesign("Robot", "Beep boop."),
        ButtonDesign("Cheeky Dog", "Woof!"),
        ButtonDesign("Duck", "Quack!"),
        ButtonDesign("Football", "Goal!"),
        ButtonDesign("Sarcastic Star", "Oh, brilliant."),
        ButtonDesign("UFO", "Alien beeps.")
    )

    private val changeHold = Runnable {
        longPressTriggered = true
        showDesignBoard()
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("snap", "Snap Button", NotificationManager.IMPORTANCE_LOW))
        startForeground(11, Notification.Builder(this, "snap")
            .setContentTitle("Snap Button is active")
            .setContentText("Tap to capture. Hold for 4 seconds to change the button.")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build())
        cameraSound.load(MediaActionSound.SHUTTER_CLICK)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                engine?.language = Locale.UK
                engine?.setPitch(1.08f)
                engine?.setSpeechRate(1.0f)
            }
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
                projection?.registerCallback(object : MediaProjection.Callback() {
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
        buttonContainer = FrameLayout(this)
        eye = ImageView(this).apply {
            setImageResource(R.drawable.file_0000000005608210b369af43ca73cd02)
            contentDescription = "Floating screenshot button"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        buttonContainer.addView(eye, FrameLayout.LayoutParams(-1, -1))
        designSymbol = TextView(this).apply {
            textSize = 24f
            gravity = Gravity.CENTER
            visibility = View.GONE
            elevation = 4f
            contentDescription = "Selected snap button design"
        }
        buttonContainer.addView(designSymbol, FrameLayout.LayoutParams(-1, -1))
        applyDesign(selectedDesign)
        buttonContainer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    handler.postDelayed(changeHold, 4000)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(changeHold)
                    if (!longPressTriggered) {
                        playButtonSound(selectedDesign, false)
                        handler.postDelayed({ takeSnap() }, 550)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(changeHold)
                    true
                }
                else -> true
            }
        }
        val size = (32 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.END; x = 0; y = 0 }
        wm.addView(buttonContainer, p)
    }

    private fun applyDesign(index: Int) {
        eye.setImageBitmap(choiceBitmap(index))
        eye.visibility = View.VISIBLE
        designSymbol?.visibility = View.GONE
        buttonContainer.background = null
    }

    private fun choiceBitmap(index: Int): Bitmap = choiceBitmaps.getOrPut(index) {
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.button_choices)
        val column = index % 4
        val row = index / 4
        val centerX = floatArrayOf(0.135f, 0.370f, 0.604f, 0.838f)[column] * sheet.width
        val centerY = if (row == 0) 0.260f * sheet.height else 0.765f * sheet.height
        val size = (sheet.width * 0.232f).toInt().coerceAtMost(sheet.height / 2)
        val left = (centerX - size / 2).toInt().coerceIn(0, sheet.width - size)
        val top = (centerY - size / 2).toInt().coerceIn(0, sheet.height - size)
        val crop = Bitmap.createBitmap(sheet, left, top, size, size)
        sheet.recycle()
        crop
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((1.5f * resources.displayMetrics.density).toInt(), 0xFFFFC94A.toInt())
    }

    private fun showDesignBoard() {
        if (chooser != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 16)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 18f * resources.displayMetrics.density
                setStroke((2 * resources.displayMetrics.density).toInt(), 0xFFFFC94A.toInt())
            }
        }
        panel.addView(TextView(this).apply {
            text = "CHOOSE YOUR SNAP BUTTON"
            textSize = 20f
            setTextColor(0xFF172332.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "Tap TEST SOUND to preview each option, then USE to change the floating button."
            textSize = 14f
            setTextColor(0xFF333333.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        designs.forEachIndexed { index, design ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 5, 4, 5)
            }
            row.addView(ImageView(this).apply {
                setImageBitmap(choiceBitmap(index))
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = design.title
            }, LinearLayout.LayoutParams((48 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt()))
            row.addView(TextView(this).apply {
                text = design.title
                textSize = 14f
                setTextColor(0xFF172332.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(8, 0, 2, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "TEST SOUND"
                textSize = 10f
                minWidth = 0
                setPadding(8, 0, 8, 0)
                setOnClickListener { playButtonSound(index, true) }
            })
            row.addView(Button(this).apply {
                text = if (index == selectedDesign) "USING" else "USE"
                textSize = 10f
                minWidth = 0
                setPadding(8, 0, 8, 0)
                isEnabled = index != selectedDesign
                setOnClickListener {
                    prefs.edit().putInt("design", index).apply()
                    applyDesign(index)
                    closeDesignBoard()
                    Toast.makeText(this@CaptureService, "${design.title} selected", Toast.LENGTH_SHORT).show()
                }
            })
            list.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(list) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        footer.addView(Button(this).apply {
            text = "TEST CAT SOUND"
            setOnClickListener { playUploadedCatSound() }
        })
        footer.addView(Button(this).apply {
            text = "REMOVE BUTTON"
            setOnClickListener { closeDesignBoard(); showRemoveConfirmation() }
        })
        footer.addView(Button(this).apply {
            text = "CLOSE"
            setOnClickListener { closeDesignBoard() }
        })
        panel.addView(footer)
        val width = (360 * resources.displayMetrics.density).toInt().coerceAtMost(resources.displayMetrics.widthPixels - 24)
        val height = (500 * resources.displayMetrics.density).toInt().coerceAtMost(resources.displayMetrics.heightPixels - 48)
        val params = WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        chooser = panel
        wm.addView(panel, params)
    }

    private fun closeDesignBoard() {
        chooser?.let { runCatching { wm.removeView(it) } }
        chooser = null
        longPressTriggered = false
    }

    private fun playButtonSound(index: Int, preview: Boolean) {
        val phrase = designs[index].preview
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, if (preview) "sound_preview" else "button_sound")
    }

    private fun playUploadedCatSound() {
        val id = resources.getIdentifier("uploaded_cat_sound", "raw", packageName)
        if (id == 0) {
            tts?.speak("Meow!", TextToSpeech.QUEUE_FLUSH, null, "cat_sound_preview")
            return
        }
        MediaPlayer.create(this, id)?.let { player ->
            player.setOnCompletionListener { it.release() }
            player.start()
        }
    }

    private fun showRemoveConfirmation() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 28, 40, 24)
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 16f * resources.displayMetrics.density }
        }
        box.addView(TextView(this).apply {
            text = "Remove Snap Button?"
            textSize = 20f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 10)
        })
        box.addView(Button(this).apply { text = "REMOVE"; setOnClickListener { stopSelf() } })
        box.addView(Button(this).apply { text = "KEEP"; setOnClickListener { runCatching { wm.removeView(box) } } })
        val params = WindowManager.LayoutParams(
            (280 * resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(box, params)
    }

    private fun takeSnap() {
        val r = reader ?: return
        while (true) { val old = r.acquireLatestImage() ?: break; old.close() }
        handler.postDelayed({
            buttonContainer.visibility = View.INVISIBLE
            handler.postDelayed({ if (::buttonContainer.isInitialized && buttonContainer.isAttachedToWindow) buttonContainer.visibility = View.VISIBLE }, 1200)
            handler.postDelayed({ captureNewest(0) }, 120)
        }, 120)
    }

    private fun captureNewest(attempt: Int) {
        val image = reader?.acquireLatestImage()
        if (image == null) {
            if (attempt < 10) handler.postDelayed({ captureNewest(attempt + 1) }, 50)
            else { buttonContainer.visibility = View.VISIBLE; Toast.makeText(this, "Try again", Toast.LENGTH_SHORT).show() }
            return
        }
        val m = resources.displayMetrics
        val plane = image.planes[0]
        val pixel = plane.pixelStride
        val row = plane.rowStride
        val raw = Bitmap.createBitmap(m.widthPixels + (row - pixel * m.widthPixels) / pixel, m.heightPixels, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(plane.buffer)
        image.close()
        val bmp = Bitmap.createBitmap(raw, 0, 0, m.widthPixels, m.heightPixels)
        raw.recycle()
        save(bmp)
        buttonContainer.visibility = View.VISIBLE
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
        closeDesignBoard()
        if (::buttonContainer.isInitialized) runCatching { wm.removeView(buttonContainer) }
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
